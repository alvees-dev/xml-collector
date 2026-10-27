package com.alveesdev.xmlcollector.xmlconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class MissingNumberFinder {

	private static final int ACCESS_KEY_LENGTH = 44;
	private static final int SERIES_START = 22;
	private static final int NUMBER_START = 25;
	private static final int NUMBER_END = 34;

	private record SeriesNumber(int series, int number) {
	}

	public static List<String> findMissing(Map<String, NfceFileData> allData) {

		Map<Integer, TreeSet<Integer>> numbersBySeries = new TreeMap<>();

		allData.forEach((fileName, data) -> {
			// A cancellation event file is not an invoice: it has no number of its own
			if (data != null && data.cancelledKey() != null) {
				return;
			}

			SeriesNumber found = identify(fileName, data);
			if (found != null) {
				numbersBySeries
						.computeIfAbsent(found.series(), series -> new TreeSet<>())
						.add(found.number());
			}
		});

		boolean multipleSeries = numbersBySeries.size() > 1;
		List<String> missing = new ArrayList<>();

		numbersBySeries.forEach((series, numbers) -> {
			Integer previous = null;

			for (int number : numbers) {
				if (previous != null && number - previous > 1) {
					missing.add(formatRange(series, previous + 1, number - 1, multipleSeries));
				}
				previous = number;
			}
		});

		return missing;
	}

	private static SeriesNumber identify(String fileName, NfceFileData data) {

		if (data != null) {
			Integer series = parseInt(data.series());
			Integer number = parseInt(data.number());

			if (series != null && number != null) {
				return new SeriesNumber(series, number);
			}
		}

		String key = fileName.replaceFirst("(?i)\\.xml$", "");

		if (key.length() == ACCESS_KEY_LENGTH && key.chars().allMatch(Character::isDigit)) {
			int series = Integer.parseInt(key.substring(SERIES_START, NUMBER_START));
			int number = Integer.parseInt(key.substring(NUMBER_START, NUMBER_END));
			return new SeriesNumber(series, number);
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

	private static String formatRange(int series, int start, int end, boolean showSeries) {
		String range = start == end ? String.valueOf(start) : start + " a " + end;
		return showSeries ? "S" + series + ": " + range : range;
	}
}