package com.alveesdev.xmlcollector.infra.xml;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.application.port.SourceFiles.ResolvedFolders;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

final class SearchFolderResolver {

	private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
	private static final String YEAR_MONTH_NAME = "[0-9]{6}";

	private final Set<Path> folders = new LinkedHashSet<>();
	private final List<Path> inaccessible = new ArrayList<>();
	private final List<String> missing = new ArrayList<>();

	private Map<String, String> dateNames = Map.of();
	private boolean matched;

	private SearchFolderResolver() {
	}

	static ResolvedFolders resolve(Path root, List<String> patterns, DateRange period) throws IOException {

		if (!Files.isDirectory(root)) {
			throw new NotDirectoryException(root.toString());
		}

		SearchFolderResolver resolver = new SearchFolderResolver();

		for (String pattern : patterns) {
			boolean optional = pattern.startsWith(SearchFolderPattern.OPTIONAL_PREFIX);
			String path = optional ? pattern.substring(SearchFolderPattern.OPTIONAL_PREFIX.length()) : pattern;

			resolver.resolvePattern(root, List.of(path.split(SearchFolderPattern.SEPARATOR)), optional, period);
		}

		return new ResolvedFolders(List.copyOf(resolver.folders), List.copyOf(resolver.inaccessible), List.copyOf(resolver.missing));
	}

	private void resolvePattern(Path root, List<String> segments, boolean optional, DateRange period) {

		YearMonth first = YearMonth.from(period.from());
		YearMonth last = YearMonth.from(period.to());

		// A folder without a date is the same for every month, so it is only looked for once.
		if (!hasDate(segments)) {
			last = first;
		}

		for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
			dateNames = dateNamesOf(month);
			matched = false;

			walk(root, segments, 0);

			if (!matched && !optional) {
				missing.add(expectedPath(root, segments));
			}
		}
	}

	private void walk(Path directory, List<String> segments, int index) {

		if (index == segments.size()) {
			folders.add(directory);
			matched = true;
			return;
		}

		String segment = segments.get(index);

		if (segment.equals(SearchFolderPattern.ANY_DEPTH)) {
			walkAnyDepth(directory, segments, index);
		} else if (dateNames.containsKey(segment)) {
			String wanted = normalized(dateNames.get(segment));

			for (Path child : subfolders(directory)) {
				if (wanted.equals(normalized(child.getFileName().toString()))) {
					walk(child, segments, index + 1);
				}
			}
		} else if (Files.isDirectory(directory.resolve(segment))) {
			walk(directory.resolve(segment), segments, index + 1);
		}
	}

	private void walkAnyDepth(Path directory, List<String> segments, int index) {

		walk(directory, segments, index + 1);

		boolean monthIsNext = index + 1 < segments.size()
				&& segments.get(index + 1).equals(SearchFolderPattern.YEAR_MONTH);

		for (Path child : subfolders(directory)) {
			// Month folders can hold thousands of files, so the search does not go through them.
			if (!monthIsNext || !child.getFileName().toString().matches(YEAR_MONTH_NAME)) {
				walkAnyDepth(child, segments, index);
			}
		}
	}

	private List<Path> subfolders(Path directory) {

		List<Path> children = new ArrayList<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			for (Path child : stream) {
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
					children.add(child);
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			inaccessible.add(directory);
		}

		return children;
	}

	private String expectedPath(Path root, List<String> segments) {

		List<String> parts = new ArrayList<>();
		parts.add(root.toString());

		for (String segment : segments) {
			parts.add(segment.equals(SearchFolderPattern.ANY_DEPTH) ? "..." : dateNames.getOrDefault(segment, segment));
		}

		return String.join(File.separator, parts);
	}

	private static boolean hasDate(List<String> segments) {
		return segments.contains(SearchFolderPattern.YEAR_MONTH)
				|| segments.contains(SearchFolderPattern.YEAR)
				|| segments.contains(SearchFolderPattern.MONTH_NAME_YEAR);
	}

	private static Map<String, String> dateNamesOf(YearMonth month) {
		return Map.of(
				SearchFolderPattern.YEAR_MONTH, YEAR_MONTH_FORMAT.format(month),
				SearchFolderPattern.YEAR, String.valueOf(month.getYear()),
				SearchFolderPattern.MONTH_NAME_YEAR, ArchiveLayout.monthDirectory(Path.of(""), month.atDay(1)).getFileName().toString());
	}

	// A folder can be written with or without the accent of its month.
	private static String normalized(String name) {
		return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}
}
