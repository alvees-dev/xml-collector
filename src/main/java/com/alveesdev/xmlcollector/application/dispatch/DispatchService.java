package com.alveesdev.xmlcollector.application.dispatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.CloudStorage;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor.CompressedFile;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.port.EmailSender;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

public class DispatchService {

	public static final long MAX_MESSAGE_BYTES = 25L * 1024 * 1024;

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

		validate(request);

		List<CompressedFile> compressed = compressAll(request.monthDirectories());
		List<Path> zipFiles = compressed.stream().map(CompressedFile::file).toList();
		long totalBytes = compressed.stream().mapToLong(CompressedFile::bytes).sum();

		String subject = subjectOf(request);
		String body = bodyOf(request);

		if (fitsInMessage(compressed)) {
			emailSender.send(request.account(), new EmailMessage(request.recipient(), subject, body, zipFiles));
			return new DispatchResult(DispatchResult.Method.ATTACHED, zipFiles, totalBytes, null);
		}

		String link = cloudStorage.uploadToSharedFolder(
				request.account().username(), folderNameOf(request.monthDirectories()), zipFiles);

		String bodyWithLink = body
				+ "\n\nOs arquivos são grandes demais para seguir em anexo e foram salvos no Google Drive."
				+ "\nAcesse pelo link:\n" + link;

		emailSender.send(request.account(), new EmailMessage(request.recipient(), subject, bodyWithLink, List.of()));

		return new DispatchResult(DispatchResult.Method.DRIVE_LINK, zipFiles, totalBytes, link);
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

		EmailAccount account = request.account();

		if (account == null || !account.isComplete()) {
			throw new DispatchException("Configure a conta de e-mail remetente em Configurações > E-mail.");
		}
	}

	private List<CompressedFile> compressAll(List<Path> monthDirectories) throws DispatchException {

		List<CompressedFile> compressed = new ArrayList<>();

		for (Path directory : monthDirectories) {
			try {
				String zipName = ArchiveLayout.zipName(archivedDocuments.cnpjsIn(directory), directory);
				Optional<CompressedFile> file = compressor.compress(directory, zipName);

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

	private static String folderNameOf(List<Path> monthDirectories) {
		return String.join(" e ", monthDirectories.stream().map(ArchiveLayout::monthLabel).toList());
	}

	private static String subjectOf(DispatchRequest request) {
		if (request.subject() != null && !request.subject().isBlank()) {
			return request.subject().trim();
		}

		return "XML NFCe - " + folderNameOf(request.monthDirectories());
	}

	private static String bodyOf(DispatchRequest request) {
		if (request.body() != null && !request.body().isBlank()) {
			return request.body();
		}

		return "Segue o XML das notas referentes a " + folderNameOf(request.monthDirectories()) + ".";
	}
}
