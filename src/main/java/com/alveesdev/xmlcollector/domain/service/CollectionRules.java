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
import com.alveesdev.xmlcollector.domain.model.NfceEntry;
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

	private record Accepted(String fileName, XmlDocument.Nfce nfce) {
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

		if (document instanceof XmlDocument.Nfce nfce) {
			return decideInvoice(sourceIndex, fileName, nfce);
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

	public List<NfceEntry> entriesOf(int sourceIndex) {

		List<NfceEntry> entries = new ArrayList<>();

		for (Accepted item : accepted(sourceIndex)) {
			entries.add(item.nfce() == null
					? NfceEntry.error(item.fileName())
					: NfceEntry.of(item.nfce(), cancelledKeys.contains(item.nfce().key()), item.fileName()));
		}

		return entries;
	}

	public SortedMap<Integer, List<NumberRange>> missingNumbers() {

		List<XmlDocument.Nfce> invoices = acceptedBySource.values().stream()
				.flatMap(List::stream)
				.map(Accepted::nfce)
				.filter(nfce -> nfce != null)
				.toList();

		return MissingNumberFinder.findBySeries(invoices);
	}

	private Decision decideInvoice(int sourceIndex, String fileName, XmlDocument.Nfce nfce) {

		if (!seenKeys.add(nfce.key())) {
			return new Skip(SkipReason.DUPLICATE);
		}

		if (!period.contains(nfce.date())) {
			return new Skip(SkipReason.OUT_OF_PERIOD);
		}

		accepted(sourceIndex).add(new Accepted(fileName, nfce));

		String key = nfce.key().value();
		return new Store(nfce.date(), fileName, key + ".xml", key);
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
