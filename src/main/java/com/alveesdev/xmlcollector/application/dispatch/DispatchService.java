package com.alveesdev.xmlcollector.application.dispatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Stage;
import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Step;
import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.CloudStorage;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor.CompressedFile;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.port.EmailSender;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

public class DispatchService {

	public static final long MAX_MESSAGE_BYTES = 25L * 1024 * 1024;
	public static final String SUBJECT_REQUIRED = "O Assunto não foi informado";

	private static final long HEADERS_AND_BODY_RESERVE_BYTES = 256L * 1024;
	private static final int BASE64_LINE_LENGTH = 76;

	private final ArchivedDocuments archivedDocuments;
	private final DirectoryCompressor compressor;
	private final EmailSender emailSender;
	private final CloudStorage cloudStorage;

	public DispatchService(ArchivedDocuments archivedDocuments, DirectoryCompressor compressor, EmailSender emailSender,
			CloudStorage cloudStorage) {
		this.archivedDocuments = archivedDocuments;
		this.compressor = compressor;
		this.emailSender = emailSender;
		this.cloudStorage = cloudStorage;
	}

	public DispatchResult dispatch(DispatchRequest request) throws DispatchException {
		return dispatch(request, progress -> {
		}, () -> false);
	}

	public DispatchResult dispatch(DispatchRequest request, Consumer<DispatchProgress> listener, BooleanSupplier cancelled)
			throws DispatchException {

		validate(request);

		Set<String> cnpjs = new TreeSet<>();
		List<CompressedFile> compressed;

		if (request.reuseExistingZip()) {
			listener.accept(new DispatchProgress(Step.COMPRESSING, Stage.SKIPPED));
			compressed = existingOf(request.monthDirectories(), cnpjs);
		} else {
			listener.accept(new DispatchProgress(Step.COMPRESSING, Stage.RUNNING));
			compressed = compressAll(request.monthDirectories(), cnpjs);
			listener.accept(new DispatchProgress(Step.COMPRESSING, Stage.DONE));
		}

		List<Path> zipFiles = compressed.stream().map(CompressedFile::file).toList();
		long totalBytes = compressed.stream().mapToLong(CompressedFile::bytes).sum();

		String subject = request.subject().trim();
		String body = bodyOf(request);

		if (fitsInMessage(compressed)) {
			listener.accept(new DispatchProgress(Step.UPLOADING, Stage.SKIPPED));
			send(request, new EmailMessage(request.recipient(), subject, body, zipFiles), listener, cancelled);
			return new DispatchResult(DispatchResult.Method.ATTACHED, zipFiles, totalBytes, null, null);
		}

		throwIfCancelled(cancelled);

		String driveFolder = driveFolderOf(cnpjs);

		listener.accept(new DispatchProgress(Step.UPLOADING, Stage.RUNNING));
		CloudStorage.UploadResult upload = cloudStorage.replaceInSharedFolder(request.account().username(), driveFolder, zipFiles);
		listener.accept(new DispatchProgress(Step.UPLOADING, Stage.DONE));

		String bodyWithLink = body
				+ "\n\nOs arquivos são grandes demais para seguir em anexo e foram salvos no Google Drive."
				+ "\nAcesse pelo link:\n" + upload.link();

		send(request, new EmailMessage(request.recipient(), subject, bodyWithLink, List.of()), listener, cancelled);

		return new DispatchResult(DispatchResult.Method.DRIVE_LINK, zipFiles, totalBytes, upload.link(), upload.cleanupFailure());
	}

	// The folder keeps only its last file, so a shared folder without a CNPJ would erase the files of other companies.
	private static String driveFolderOf(Set<String> cnpjs) throws DispatchException {

		if (cnpjs.isEmpty()) {
			throw new DispatchException("Não foi possível identificar o CNPJ das notas para escolher a pasta no Google Drive.");
		}

		return String.join("_", cnpjs);
	}

	private void send(DispatchRequest request, EmailMessage message, Consumer<DispatchProgress> listener, BooleanSupplier cancelled)
			throws DispatchException {

		throwIfCancelled(cancelled);

		listener.accept(new DispatchProgress(Step.SENDING, Stage.RUNNING));
		emailSender.send(request.account(), message);
		listener.accept(new DispatchProgress(Step.SENDING, Stage.DONE));
	}

	private static void throwIfCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Envio cancelado");
		}
	}

	public static long encodedSize(long bytes) {
		long base64 = ((bytes + 2) / 3) * 4;
		long lineBreaks = ((base64 + BASE64_LINE_LENGTH - 1) / BASE64_LINE_LENGTH) * 2;

		return base64 + lineBreaks;
	}

	public static boolean fitsInMessage(List<CompressedFile> files) {
		long total = HEADERS_AND_BODY_RESERVE_BYTES;

		for (CompressedFile file : files) {
			total += encodedSize(file.bytes());
		}

		return total <= MAX_MESSAGE_BYTES;
	}

	private void validate(DispatchRequest request) throws DispatchException {

		if (request.monthDirectories().isEmpty()) {
			throw new DispatchException("Não há pasta de mês coletada para enviar.");
		}

		if (request.recipient() == null || !request.recipient().contains("@")) {
			throw new DispatchException("Informe um e-mail destinatário válido na aba Envio de XML.");
		}

		if (request.subject() == null || request.subject().isBlank()) {
			throw new DispatchException(SUBJECT_REQUIRED);
		}

		EmailAccount account = request.account();

		if (account == null || !account.isComplete()) {
			throw new DispatchException("Configure a conta de e-mail remetente em Configurações > E-mail.");
		}
	}

	public Optional<CompressedFile> existingZipOf(Path monthDirectory) throws DispatchException {
		try {
			return compressor.existing(monthDirectory);
		} catch (IOException e) {
			throw new DispatchException("Não foi possível verificar o arquivo compactado de " + ArchiveLayout.monthLabel(monthDirectory) + ": " + e.getMessage(), e);
		}
	}

	private List<CompressedFile> existingOf(List<Path> monthDirectories, Set<String> cnpjs) throws DispatchException {

		List<CompressedFile> existing = new ArrayList<>();

		for (Path directory : monthDirectories) {
			Optional<CompressedFile> file = existingZipOf(directory);

			if (file.isEmpty()) {
				throw new DispatchException("O arquivo compactado de " + ArchiveLayout.monthLabel(directory) + " não foi encontrado.");
			}

			try {
				cnpjs.addAll(archivedDocuments.cnpjsIn(directory));
			} catch (IOException e) {
				throw new DispatchException("Não foi possível ler a pasta " + ArchiveLayout.monthLabel(directory) + ": " + e.getMessage(), e);
			}

			existing.add(file.get());
		}

		return existing;
	}

	private List<CompressedFile> compressAll(List<Path> monthDirectories, Set<String> cnpjs) throws DispatchException {

		List<CompressedFile> compressed = new ArrayList<>();

		for (Path directory : monthDirectories) {
			try {
				Set<String> ofMonth = archivedDocuments.cnpjsIn(directory);
				cnpjs.addAll(ofMonth);

				String zipName = ArchiveLayout.zipName(ofMonth, directory);
				Optional<CompressedFile> file = compressor.compress(directory, zipName, List.of(valuesReportOf(directory)));

				if (file.isEmpty()) {
					throw new DispatchException("A pasta " + ArchiveLayout.monthLabel(directory) + " não tem arquivos para enviar.");
				}

				compressed.add(file.get());

			} catch (IOException e) {
				throw new DispatchException("Falha ao compactar a pasta " + ArchiveLayout.monthLabel(directory) + ": " + e.getMessage(), e);
			}
		}

		return compressed;
	}

	private DirectoryCompressor.ExtraFile valuesReportOf(Path directory) throws IOException {

		ValuesReport report = ValuesReport.of(archivedDocuments.documentsOfTheCompanyIn(directory));
		String month = directory.getFileName() + " de " + directory.getParent().getFileName();

		return new DirectoryCompressor.ExtraFile(ValuesReportText.FILE_NAME, ValuesReportText.bytesOf(month, report));
	}

	private static String bodyOf(DispatchRequest request) {
		return request.body() == null || request.body().isBlank() ? EmailDraft.DEFAULT_BODY : request.body();
	}
}
