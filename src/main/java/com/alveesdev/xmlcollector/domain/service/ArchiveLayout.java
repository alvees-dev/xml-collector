package com.alveesdev.xmlcollector.domain.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public final class ArchiveLayout {

	private static final String[] MONTH_NAMES = {
			"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
			"Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
	};

	private static final DateTimeFormatter DAY_FOLDER_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private ArchiveLayout() {
	}

	public static List<String> monthNames() {
		return List.of(MONTH_NAMES);
	}

	public static Path monthDirectory(Path archiveRoot, LocalDate date) {
		return archiveRoot.resolve(MONTH_NAMES[date.getMonthValue() - 1] + "_" + date.getYear());
	}

	public static List<Path> monthDirectories(Path archiveRoot, DateRange period) {
		List<Path> directories = new ArrayList<>();

		for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(YearMonth.from(period.to())); month = month.plusMonths(1)) {
			directories.add(monthDirectory(archiveRoot, month.atDay(1)));
		}

		return directories;
	}

	public static Path directoryFor(Path archiveRoot, LocalDate date, OrganizeMode mode, DocumentCategory category) {
		Path categoryDirectory = monthDirectory(archiveRoot, date).resolve(category.folderName());

		return mode == OrganizeMode.BY_DAY
				? categoryDirectory.resolve(DAY_FOLDER_FORMAT.format(date))
				: categoryDirectory;
	}
}