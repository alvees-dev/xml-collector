package com.alveesdev.xmlcollector.domain.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class CollectionRules {

	public sealed interface Decision {
	}

	public record Store(LocalDate date, String preferredName, String canonicalName, String identity) implements Decision {
	}

	public record Skip(SkipReason reason) implements Decision {
	}

	public enum SkipReason {
		DUPLICATE,
		OUT_OF_PERIOD,
		IGNORED,
		UNREADABLE
	}

	private record Accepted(String fileName, XmlDocument.Invoice invoice) {
	}

	private final DateRange period;
	private final Set<AccessKey> seenKeys = new HashSet<>();
	private final Set<String> seenEventIds = new HashSet<>();
	private final Set<AccessKey> cancelledKeys = new HashSet<>();
	private final Map<Integer, List<Accepted>> acceptedBySource = new HashMap<>();

	public CollectionRules(DateRange period) {
		this.period = period;
	}

	public Decision decide(int sourceIndex, String fileName, XmlDocument document) {

		if (document instanceof XmlDocument.Invoice invoice) {
			return decideInvoice(sourceIndex, fileName, invoice);
		}

		if (document instanceof XmlDocument.CancelEvent event) {
			return decideCancelEvent(event);
		}

		if (document instanceof XmlDocument.Ignored) {
			return new Skip(SkipReason.IGNORED);
		}

		accepted(sourceIndex).add(new Accepted(fileName, null));
		return new Skip(SkipReason.UNREADABLE);
	}

	public List<DocumentEntry> entriesOf(int sourceIndex) {

		List<DocumentEntry> entries = new ArrayList<>();

		for (Accepted item : accepted(sourceIndex)) {
			entries.add(item.invoice() == null
					? DocumentEntry.error(item.fileName())
					: DocumentEntry.of(item.invoice(), cancelledKeys.contains(item.invoice().key()), item.fileName()));
		}

		return entries;
	}

	public SortedMap<Integer, List<NumberRange>> missingNumbers() {

		List<XmlDocument.Invoice> invoices = acceptedBySource.values().stream()
				.flatMap(List::stream)
				.map(Accepted::invoice)
				.filter(invoice -> invoice != null)
				.toList();

		return MissingNumberFinder.findBySeries(invoices);
	}

	private Decision decideInvoice(int sourceIndex, String fileName, XmlDocument.Invoice invoice) {

		if (!seenKeys.add(invoice.key())) {
			return new Skip(SkipReason.DUPLICATE);
		}

		if (!period.contains(invoice.date())) {
			return new Skip(SkipReason.OUT_OF_PERIOD);
		}

		accepted(sourceIndex).add(new Accepted(fileName, invoice));

		String key = invoice.key().value();
		return new Store(invoice.date(), fileName, key + ".xml", key);
	}

	private Decision decideCancelEvent(XmlDocument.CancelEvent event) {

		cancelledKeys.add(event.cancelledKey());

		if (!seenEventIds.add(event.eventId())) {
			return new Skip(SkipReason.DUPLICATE);
		}

		if (!period.contains(event.date())) {
			return new Skip(SkipReason.OUT_OF_PERIOD);
		}

		String name = event.eventId() + ".xml";
		return new Store(event.date(), name, name, event.eventId());
	}

	private List<Accepted> accepted(int sourceIndex) {
		return acceptedBySource.computeIfAbsent(sourceIndex, index -> new ArrayList<>());
	}
}
