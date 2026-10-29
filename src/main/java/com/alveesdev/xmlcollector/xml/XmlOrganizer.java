package com.alveesdev.xmlcollector.xml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.config.OrganizeMode;

public class XmlOrganizer {

	private static final String[] MONTH_NAMES = {
			"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
			"Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
	};

	private static final DateTimeFormatter DAY_FOLDER_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	public static class CopyReport {

		private int copied;
		private int alreadyExisting;
		private int outOfPeriod;
		private int unreadable;
		private final List<String> failures = new ArrayList<>();

		public int getCopied() {
			return copied;
		}

		public int getAlreadyExisting() {
			return alreadyExisting;
		}

		public int getOutOfPeriod() {
			return outOfPeriod;
		}

		public int getUnreadable() {
			return unreadable;
		}

		public List<String> getFailures() {
			return failures;
		}

		public void addFailure(String failure) {
			failures.add(failure);
		}
	}

	public static void copyDirectory(Path sourceDirectory, LocalDate from, LocalDate to,
			Path archiveRoot, OrganizeMode mode, CopyReport report) throws IOException {

		Path normalizedArchive = archiveRoot.toAbsolutePath().normalize();

		List<Path> xmlFiles;
		try (Stream<Path> walk = Files.walk(sourceDirectory)) {
			xmlFiles = walk
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().toLowerCase().endsWith(".xml"))
					.filter(path -> !path.toAbsolutePath().normalize().startsWith(normalizedArchive))
					.collect(Collectors.toList());
		}

		for (Path xmlFile : xmlFiles) {
			LocalDate date = readDate(xmlFile);

			if (date == null) {
				report.unreadable++;
				continue;
			}

			if (date.isBefore(from) || date.isAfter(to)) {
				report.outOfPeriod++;
				continue;
			}

			Path targetDirectory = targetDirectory(archiveRoot, date, mode);
			Path target = targetDirectory.resolve(xmlFile.getFileName());

			if (Files.exists(target)) {
				report.alreadyExisting++;
				continue;
			}

			try {
				Files.createDirectories(targetDirectory);
				Files.copy(xmlFile, target, StandardCopyOption.COPY_ATTRIBUTES);
				report.copied++;
			} catch (IOException e) {
				report.addFailure("Falha ao copiar " + xmlFile.getFileName());
			}
		}
	}

	private static LocalDate readDate(Path xmlFile) {
		try {
			return XmlDateReader.getDocumentDate(XmlReader.loadXML(xmlFile.toFile()));
		} catch (Exception e) {
			return null;
		}
	}

	private static Path targetDirectory(Path archiveRoot, LocalDate date, OrganizeMode mode) {
		String monthFolder = MONTH_NAMES[date.getMonthValue() - 1] + "_" + date.getYear();
		Path monthDirectory = archiveRoot.resolve(monthFolder);

		return mode == OrganizeMode.BY_DAY
				? monthDirectory.resolve(DAY_FOLDER_FORMAT.format(date))
				: monthDirectory;
	}
}