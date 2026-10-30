package com.alveesdev.xmlcollector.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument.Nfce;

public final class MissingNumberFinder {

	private MissingNumberFinder() {
	}

	public static SortedMap<Integer, List<NumberRange>> findBySeries(Collection<Nfce> invoices) {

		SortedMap<Integer, TreeSet<Integer>> numbersBySeries = new TreeMap<>();

		for (Nfce invoice : invoices) {
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