package com.alveesdev.xmlcollector.application;

import java.util.List;
import java.util.SortedMap;

import com.alveesdev.xmlcollector.domain.model.NfceEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public record CollectionResult(
		List<SourceResult> sources,
		CollectionSummary summary,
		List<SourceFailure> failures
) {

	public record SourceResult(
			int sourceIndex,
			List<NfceEntry> entries,
			SortedMap<Integer, List<NumberRange>> missingBySeries
	) {
	}

	public List<NfceEntry> entries() {
		return sources.stream()
				.flatMap(source -> source.entries().stream())
				.toList();
	}
}