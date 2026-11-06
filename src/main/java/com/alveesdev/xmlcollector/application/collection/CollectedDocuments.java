package com.alveesdev.xmlcollector.application.collection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public final class CollectedDocuments {

	private final List<DocumentEntry> entries = new ArrayList<>();
	private final Set<AccessKey> keys = new HashSet<>();
	private final Set<AccessKey> cancelledKeys = new HashSet<>();
	private final SortedMap<Integer, List<NumberRange>> voidedInPeriod = new TreeMap<>();

	public static CollectedDocuments from(CollectionResult result) {
		CollectedDocuments collected = new CollectedDocuments();

		collected.cancelledKeys.addAll(result.cancelledKeys());
		collected.voidedInPeriod.putAll(result.voidedInPeriod());

		for (DocumentEntry entry : result.entries()) {
			if (entry.invoice() == null) {
				collected.entries.add(entry);
			} else {
				collected.addInvoice(entry);
			}
		}

		return collected;
	}

	public void addFound(CollectionResult result) {
		cancelledKeys.addAll(result.cancelledKeys());

		for (DocumentEntry entry : result.entries()) {
			if (entry.invoice() != null) {
				addInvoice(entry);
			}
		}
	}

	public List<DocumentEntry> entries() {
		return entries.stream()
				.map(this::withCurrentStatus)
				.toList();
	}

	public SortedMap<Integer, List<NumberRange>> voidedInPeriod() {
		return Collections.unmodifiableSortedMap(voidedInPeriod);
	}

	public BigDecimal authorizedTotal() {
		return entries().stream()
				.filter(entry -> entry.status() == DocumentStatus.AUTHORIZED)
				.map(entry -> entry.invoice().totalValue())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private void addInvoice(DocumentEntry entry) {
		if (keys.add(entry.invoice().key())) {
			entries.add(entry);
		}
	}

	private DocumentEntry withCurrentStatus(DocumentEntry entry) {
		boolean cancelled = entry.invoice() != null && cancelledKeys.contains(entry.invoice().key());

		return cancelled ? new DocumentEntry(DocumentStatus.CANCELLED, entry.fileName(), entry.invoice()) : entry;
	}
}
