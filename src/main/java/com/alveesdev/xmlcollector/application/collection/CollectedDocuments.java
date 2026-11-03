package com.alveesdev.xmlcollector.application.collection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;

public final class CollectedDocuments {

	private final List<DocumentEntry> entries = new ArrayList<>();
	private final Set<AccessKey> keys = new HashSet<>();
	private final Set<AccessKey> cancelledKeys = new HashSet<>();

	public static CollectedDocuments from(CollectionResult result) {
		CollectedDocuments collected = new CollectedDocuments();

		collected.cancelledKeys.addAll(result.cancelledKeys());

		for (DocumentEntry entry : result.entries()) {
			if (entry.invoice() == null) {
				collected.entries.add(entry);
			} else {
				collected.addInvoice(entry);
			}
		}

		return collected;
	}

	public List<DocumentEntry> entries() {
		return entries.stream()
				.map(this::withCurrentStatus)
				.toList();
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
