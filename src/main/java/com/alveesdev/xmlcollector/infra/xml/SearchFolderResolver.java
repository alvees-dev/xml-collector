package com.alveesdev.xmlcollector.infra.xml;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.alveesdev.xmlcollector.application.port.SourceFiles.ResolvedFolders;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;

final class SearchFolderResolver {

	private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
	private static final String YEAR_MONTH_NAME = "[0-9]{6}";

	private final Set<Path> folders = new LinkedHashSet<>();
	private final List<Path> inaccessible = new ArrayList<>();
	private final Set<String> monthsOfPeriod;

	private SearchFolderResolver(DateRange period) {
		this.monthsOfPeriod = monthsOf(period);
	}

	static ResolvedFolders resolve(Path root, List<String> patterns, DateRange period) throws IOException {

		if (!Files.isDirectory(root)) {
			throw new NotDirectoryException(root.toString());
		}

		SearchFolderResolver resolver = new SearchFolderResolver(period);

		for (String pattern : patterns) {
			resolver.walk(root, List.of(pattern.split(SearchFolderPattern.SEPARATOR)), 0);
		}

		return new ResolvedFolders(List.copyOf(resolver.folders), List.copyOf(resolver.inaccessible));
	}

	private void walk(Path directory, List<String> segments, int index) {

		if (index == segments.size()) {
			folders.add(directory);
			return;
		}

		String segment = segments.get(index);

		if (segment.equals(SearchFolderPattern.ANY_DEPTH)) {
			walkAnyDepth(directory, segments, index);
		} else if (segment.equals(SearchFolderPattern.YEAR_MONTH)) {
			for (Path child : subfolders(directory)) {
				if (monthsOfPeriod.contains(child.getFileName().toString())) {
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

	private static Set<String> monthsOf(DateRange period) {

		Set<String> months = new HashSet<>();

		for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(YearMonth.from(period.to())); month = month.plusMonths(1)) {
			months.add(YEAR_MONTH_FORMAT.format(month));
		}

		return months;
	}
}
