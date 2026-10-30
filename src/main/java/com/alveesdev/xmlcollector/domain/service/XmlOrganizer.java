package com.alveesdev.xmlcollector.domain.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.alveesdev.xmlcollector.domain.model.CopyReport;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public class XmlOrganizer {

	private static final String[] MONTH_NAMES = {
			"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
			"Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
	};

	private static final DateTimeFormatter DAY_FOLDER_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	public static void copyFile(Path xmlFile, LocalDate date, Path archiveRoot, OrganizeMode mode,
			CopyReport report) {

		Path targetDirectory = targetDirectory(archiveRoot, date, mode);
		Path target = targetDirectory.resolve(xmlFile.getFileName());

		if (Files.exists(target)) {
			report.incrementAlreadyExisting();
			return;
		}

		try {
			Files.createDirectories(targetDirectory);
			Files.copy(xmlFile, target, StandardCopyOption.COPY_ATTRIBUTES);
			report.incrementCopied();
		} catch (IOException e) {
			report.addFailure("Falha ao copiar " + xmlFile.getFileName());
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