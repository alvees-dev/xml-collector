package com.alveesdev.xmlcollector.domain.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public final class ArchiveLayout {

	private static final String[] MONTH_NAMES = {
			"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
			"Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
	};

	private static final DateTimeFormatter DAY_FOLDER_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private static final String ZIP_PREFIX = "ArquivoXML";
	private static final String VOIDING_FILE_SUFFIX = "-procInutNFe.xml";
	private static final String PDV_FOLDER_PREFIX = "PDV";

	private ArchiveLayout() {
	}

	public static List<String> monthNames() {
		return List.of(MONTH_NAMES);
	}

	public static String monthYearName(LocalDate date) {
		return MONTH_NAMES[date.getMonthValue() - 1] + "_" + date.getYear();
	}

	public static Path monthDirectory(Path archiveRoot, LocalDate date) {
		return archiveRoot.resolve(String.valueOf(date.getYear())).resolve(MONTH_NAMES[date.getMonthValue() - 1]);
	}

	public static String monthLabel(Path monthDirectory) {
		return monthDirectory.getFileName() + "_" + monthDirectory.getParent().getFileName();
	}

	public static List<Path> monthDirectories(Path archiveRoot, DateRange period) {
		List<Path> directories = new ArrayList<>();

		for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(YearMonth.from(period.to())); month = month.plusMonths(1)) {
			directories.add(monthDirectory(archiveRoot, month.atDay(1)));
		}

		return directories;
	}

	public static String zipName(Collection<String> cnpjs, Path monthDirectory) {
		List<String> parts = new ArrayList<>();
		parts.add(ZIP_PREFIX);
		parts.addAll(new TreeSet<>(cnpjs));
		parts.add(monthLabel(monthDirectory));

		return String.join("_", parts);
	}

	public static String voidingFileName(String voidingId) {
		return voidingId.replaceFirst("^ID", "") + VOIDING_FILE_SUFFIX;
	}

	public static Path directoryFor(Path archiveRoot, LocalDate date, OrganizeMode mode, DocumentCategory category) {
		return withDay(monthDirectory(archiveRoot, date).resolve(category.folderName()), date, mode);
	}

	public static Path pdvDirectoryFor(Path archiveRoot, LocalDate date, OrganizeMode mode, int sourceIndex) {
		return withDay(monthDirectory(archiveRoot, date).resolve(PDV_FOLDER_PREFIX + (sourceIndex + 1)), date, mode);
	}

	private static Path withDay(Path directory, LocalDate date, OrganizeMode mode) {
		return mode == OrganizeMode.BY_DAY ? directory.resolve(DAY_FOLDER_FORMAT.format(date)) : directory;
	}
}