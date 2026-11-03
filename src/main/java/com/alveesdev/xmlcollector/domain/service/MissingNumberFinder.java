package com.alveesdev.xmlcollector.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument.Invoice;

public final class MissingNumberFinder {

	private MissingNumberFinder() {
	}

	public static SortedMap<Integer, List<NumberRange>> findBySeries(Collection<Invoice> invoices,
			Map<Integer, List<NumberRange>> voidedBySeries) {

		SortedMap<Integer, List<NumberRange>> missingBySeries = new TreeMap<>();

		findBySeries(invoices).forEach((series, gaps) -> missingBySeries.put(
				series, subtract(gaps, voidedBySeries.getOrDefault(series, List.of()))));

		return missingBySeries;
	}

	public static List<NumberRange> subtract(List<NumberRange> ranges, List<NumberRange> removed) {

		List<NumberRange> remaining = ranges;

		for (NumberRange cut : removed) {
			List<NumberRange> next = new ArrayList<>();

			for (NumberRange range : remaining) {
				if (cut.end() < range.start() || cut.start() > range.end()) {
					next.add(range);
					continue;
				}

				if (range.start() < cut.start()) {
					next.add(new NumberRange(range.start(), cut.start() - 1));
				}

				if (cut.end() < range.end()) {
					next.add(new NumberRange(cut.end() + 1, range.end()));
				}
			}

			remaining = next;
		}

		return remaining;
	}

	public static SortedMap<Integer, List<NumberRange>> findBySeries(Collection<Invoice> invoices) {

		SortedMap<Integer, TreeSet<Integer>> numbersBySeries = new TreeMap<>();

		for (Invoice invoice : invoices) {
			numbersBySeries
					.computeIfAbsent(invoice.series(), series -> new TreeSet<>())
					.add(invoice.number());
		}

		SortedMap<Integer, List<NumberRange>> missingBySeries = new TreeMap<>();

		numbersBySeries.forEach((series, numbers) -> missingBySeries.put(series, findGaps(numbers)));

		return missingBySeries;
	}

	private static List<NumberRange> findGaps(TreeSet<Integer> numbers) {

		List<NumberRange> gaps = new ArrayList<>();
		Integer previous = null;

		for (int number : numbers) {
			if (previous != null && number - previous > 1) {
				gaps.add(new NumberRange(previous + 1, number - 1));
			}
			previous = number;
		}

		return gaps;
	}
}