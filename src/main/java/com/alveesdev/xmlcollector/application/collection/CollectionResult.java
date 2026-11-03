package com.alveesdev.xmlcollector.application.collection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public record CollectionResult(
		List<SourceResult> sources,
		SortedMap<Integer, List<NumberRange>> missingBySeries,
		CollectionSummary summary,
		List<SourceFailure> failures,
		String historyFailure,
		Set<AccessKey> cancelledKeys
) {

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

	public List<DocumentEntry> entries() {
		return sources.stream()
				.flatMap(source -> source.entries().stream())
				.toList();
	}}