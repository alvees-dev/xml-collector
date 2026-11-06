package com.alveesdev.xmlcollector.application.collection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;

public record CollectionResult(
		List<SourceResult> sources,
		SortedMap<Integer, List<NumberRange>> missingBySeries,
		CollectionSummary summary,
		List<SourceFailure> failures,
		String historyFailure,
		Set<AccessKey> cancelledKeys,
		SortedMap<Integer, List<NumberRange>> voidedInPeriod
) {

	// Without voidings, as most results that do not come from a collection.
	public CollectionResult(List<SourceResult> sources, SortedMap<Integer, List<NumberRange>> missingBySeries, CollectionSummary summary,
			List<SourceFailure> failures, String historyFailure, Set<AccessKey> cancelledKeys) {
		this(sources, missingBySeries, summary, failures, historyFailure, cancelledKeys, new TreeMap<>());
	}

	public int voidedNumbers() {
		return voidedInPeriod.values().stream().flatMap(List::stream).mapToInt(NumberRange::size).sum();
	}

	public record SourceResult(int sourceIndex, List<DocumentEntry> entries) {
	}

	public Map<Integer, Set<Integer>> sourcesBySeries() {
		Map<Integer, Set<Integer>> sourcesBySeries = new HashMap<>();

		for (SourceResult source : sources) {
			for (DocumentEntry entry : source.entries()) {
				if (entry.invoice() != null) {
					sourcesBySeries.computeIfAbsent(entry.invoice().series(), series -> new HashSet<>()).add(source.sourceIndex());
				}
			}
		}

		return sourcesBySeries;
	}

	public Map<Integer, Set<SeriesOwner>> ownersBySeries() {
		Map<Integer, Set<SeriesOwner>> owners = new HashMap<>();

		for (DocumentEntry entry : entries()) {
			if (entry.invoice() != null) {
				owners.computeIfAbsent(entry.invoice().series(), series -> new HashSet<>()).add(SeriesOwner.of(entry.invoice().key()));
			}
		}

		return owners;
	}

	public Map<Integer, Integer> firstNumberBySeries() {
		Map<Integer, Integer> first = new HashMap<>();

		for (DocumentEntry entry : entries()) {
			if (entry.invoice() != null) {
				first.merge(entry.invoice().series(), entry.invoice().number(), Math::min);
			}
		}

		return first;
	}

	public List<DocumentEntry> entries() {
		return sources.stream()
				.flatMap(source -> source.entries().stream())
				.toList();
	}
}