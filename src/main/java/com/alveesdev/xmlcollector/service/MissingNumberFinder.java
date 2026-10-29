package com.alveesdev.xmlcollector.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.model.NfceFileData;
import com.alveesdev.xmlcollector.xml.XmlKeyReader;

public class MissingNumberFinder {

	private static final int SERIES_START = 22;
	private static final int NUMBER_START = 25;
	private static final int NUMBER_END = 34;

	private record SeriesNumber(int series, int number) {
	}

	public static Map<Integer, List<String>> findMissingBySeries(Collection<NfceFileData> invoices) {

		Map<Integer, TreeSet<Integer>> numbersBySeries = new TreeMap<>();

		for (NfceFileData data : invoices) {
			if (data == null || data.cancelledKey() != null) {
				continue;
			}

			SeriesNumber found = identify(data);

			if (found != null) {
				numbersBySeries
						.computeIfAbsent(found.series(), series -> new TreeSet<>())
						.add(found.number());
			}
		}

		Map<Integer, List<String>> missingBySeries = new TreeMap<>();

		numbersBySeries.forEach((series, numbers) -> {
			List<String> ranges = new ArrayList<>();
			Integer previous = null;

			for (int number : numbers) {
				if (previous != null && number - previous > 1) {
					ranges.add(formatRange(previous + 1, number - 1));
				}
				previous = number;
			}

			missingBySeries.put(series, ranges);
		});

		return missingBySeries;
	}

	public static List<String> findMissing(Collection<NfceFileData> invoices) {

		Map<Integer, List<String>> missingBySeries = findMissingBySeries(invoices);
		boolean multipleSeries = missingBySeries.size() > 1;

		List<String> missing = new ArrayList<>();

		missingBySeries.forEach((series, ranges) -> {
			for (String range : ranges) {
				missing.add(multipleSeries ? "S" + series + ": " + range : range);
			}
		});

		return missing;
	}

	private static SeriesNumber identify(NfceFileData data) {

		Integer series = parseInt(data.series());
		Integer number = parseInt(data.number());

		if (series != null && number != null) {
			return new SeriesNumber(series, number);
		}

		String key = data.accessKey();

		if (XmlKeyReader.isValidKey(key)) {
			return new SeriesNumber(
					Integer.parseInt(key.substring(SERIES_START, NUMBER_START)),
					Integer.parseInt(key.substring(NUMBER_START, NUMBER_END)));
		}

		return null;
	}

	private static Integer parseInt(String value) {
		if (value == null) {
			return null;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String formatRange(int start, int end) {
		return start == end ? String.valueOf(start) : start + " a " + end;
	}
}