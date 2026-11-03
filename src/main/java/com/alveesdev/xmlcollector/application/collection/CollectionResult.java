package com.alveesdev.xmlcollector.application.collection;

import java.util.List;
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

	public List<DocumentEntry> entries() {
		return sources.stream()
				.flatMap(source -> source.entries().stream())
				.toList();
	}
}