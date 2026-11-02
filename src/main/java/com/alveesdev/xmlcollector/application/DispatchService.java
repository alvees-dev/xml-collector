package com.alveesdev.xmlcollector.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.infra.archive.MonthZipArchiver;

public class DispatchService {

	public static final long MAX_MESSAGE_BYTES = 25L * 1024 * 1024;

	private static final long HEADERS_AND_BODY_RESERVE_BYTES = 256L * 1024;
	private static final int BASE64_LINE_LENGTH = 76;

	private final MonthZipArchiver archiver;
	private final EmailSender emailSender;
	private final CloudStorage cloudStorage;

	public DispatchService(MonthZipArchiver archiver, EmailSender emailSender, CloudStorage cloudStorage) {
		this.archiver = archiver;
		this.emailSender = emailSender;
		this.cloudStorage = cloudStorage;
	}

	public DispatchResult dispatch(DispatchRequest request) throws DispatchException {

		validate(request);

		List<Path> zipFiles = zipAll(request.monthDirectories());
		long totalBytes = totalSize(zipFiles);

		String subject = subjectOf(request);
		String body = bodyOf(request);

		if (fitsInMessage(zipFiles)) {
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

	public static boolean fitsInMessage(List<Path> files) throws DispatchException {
		long total = HEADERS_AND_BODY_RESERVE_BYTES;

		for (Path file : files) {
			try {
				total += encodedSize(Files.size(file));
			} catch (IOException e) {
				throw new DispatchException("Não foi possível ler o tamanho do arquivo " + file.getFileName(), e);
			}
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

	private List<Path> zipAll(List<Path> monthDirectories) throws DispatchException {

		List<Path> zipFiles = new ArrayList<>();

		for (Path directory : monthDirectories) {
			try {
				MonthZipArchiver.ZipResult zipResult = archiver.zip(directory);

				if (zipResult.entries() == 0) {
					Files.deleteIfExists(zipResult.file());
					throw new DispatchException("A pasta " + directory.getFileName() + " não tem arquivos para enviar.");
				}

				zipFiles.add(zipResult.file());

			} catch (IOException e) {
				throw new DispatchException("Falha ao compactar a pasta " + directory.getFileName() + ": " + e.getMessage(), e);
			}
		}

		return zipFiles;
	}

	private static long totalSize(List<Path> files) throws DispatchException {
		long total = 0;

		for (Path file : files) {
			try {
				total += Files.size(file);
			} catch (IOException e) {
				throw new DispatchException("Não foi possível ler o tamanho do arquivo " + file.getFileName(), e);
			}
		}

		return total;
	}

	private static String folderNameOf(List<Path> monthDirectories) {
		return String.join(" e ", monthDirectories.stream().map(directory -> directory.getFileName().toString()).toList());
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