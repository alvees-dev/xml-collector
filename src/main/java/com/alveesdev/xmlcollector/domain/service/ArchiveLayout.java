package com.alveesdev.xmlcollector.domain.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public final class ArchiveLayout {

	private static final String[] MONTH_NAMES = {
			"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
			"Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
	};

	private static final DateTimeFormatter DAY_FOLDER_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private ArchiveLayout() {
	}

	public static Path directoryFor(Path archiveRoot, LocalDate date, OrganizeMode mode) {
		String monthFolder = MONTH_NAMES[date.getMonthValue() - 1] + "_" + date.getYear();
		Path monthDirectory = archiveRoot.resolve(monthFolder);

		return mode == OrganizeMode.BY_DAY
				? monthDirectory.resolve(DAY_FOLDER_FORMAT.format(date))
				: monthDirectory;
	}
}