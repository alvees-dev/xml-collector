package com.alveesdev.xmlcollector.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import com.alveesdev.xmlcollector.application.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.CollectionSummary.CopyFailure;
import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.NfceEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.xml.NfceXmlReader;

class SourceProcessor {

	private record Pending(String fileName, XmlDocument.Nfce nfce) {
	}

	private static class Counters {
		private int copied;
		private int renamed;
		private int replaced;
		private int alreadyExisting;
		private int duplicates;
		private int outOfPeriod;
		private int ignored;
		private int errors;
		private final List<CopyFailure> copyFailures = new ArrayList<>();
	}

	private final CollectionRequest request;
	private final NfceXmlReader reader;
	private final XmlArchive archive;

	private final Counters counters = new Counters();
	private final Set<AccessKey> seenKeys = new HashSet<>();
	private final Set<String> seenEventIds = new HashSet<>();

	SourceProcessor(CollectionRequest request, NfceXmlReader reader, XmlArchive archive) {
		this.request = request;
		this.reader = reader;
		this.archive = archive;
	}

	SourceResult process(int sourceIndex, List<Path> files, Runnable onFileDone, BooleanSupplier cancelled) {

		Set<AccessKey> cancelledKeys = new HashSet<>();
		List<Pending> pending = new ArrayList<>();

		for (Path file : files) {
			throwIfCancelled(cancelled);
			handle(file, cancelledKeys, pending);
			onFileDone.run();
		}

		List<NfceEntry> entries = new ArrayList<>();
		List<XmlDocument.Nfce> invoices = new ArrayList<>();

		for (Pending item : pending) {
			if (item.nfce() == null) {
				entries.add(NfceEntry.error(item.fileName()));
				continue;
			}

			entries.add(NfceEntry.of(item.nfce(), cancelledKeys.contains(item.nfce().key()), item.fileName()));
			invoices.add(item.nfce());
		}

		SortedMap<Integer, List<NumberRange>> missing = MissingNumberFinder.findBySeries(invoices);

		return new SourceResult(sourceIndex, entries, missing);
	}

	CollectionSummary summary() {
		return new CollectionSummary(
				counters.copied,
				counters.renamed,
				counters.replaced,
				counters.alreadyExisting,
				counters.duplicates,
				counters.outOfPeriod,
				counters.ignored,
				counters.errors,
				List.copyOf(counters.copyFailures));
	}

	private void handle(Path file, Set<AccessKey> cancelledKeys, List<Pending> pending) {

		String fileName = file.getFileName().toString();
		XmlDocument document = reader.read(file);

		if (document instanceof XmlDocument.Nfce nfce) {
			handleInvoice(file, fileName, nfce, pending);
		} else if (document instanceof XmlDocument.CancelEvent event) {
			handleCancelEvent(file, event, cancelledKeys);
		} else if (document instanceof XmlDocument.Ignored) {
			counters.ignored++;
		} else {
			counters.errors++;
			pending.add(new Pending(fileName, null));
		}
	}

	private void handleInvoice(Path file, String fileName, XmlDocument.Nfce nfce, List<Pending> pending) {

		if (!seenKeys.add(nfce.key())) {
			counters.duplicates++;
			return;
		}

		if (!request.period().contains(nfce.date())) {
			counters.outOfPeriod++;
			return;
		}

		pending.add(new Pending(fileName, nfce));

		String key = nfce.key().value();
		store(file, nfce.date(), fileName, key + ".xml", key);
	}

	private void handleCancelEvent(Path file, XmlDocument.CancelEvent event, Set<AccessKey> cancelledKeys) {

		cancelledKeys.add(event.cancelledKey());

		if (!seenEventIds.add(event.eventId())) {
			counters.duplicates++;
			return;
		}

		if (!request.period().contains(event.date())) {
			counters.outOfPeriod++;
			return;
		}

		String name = event.eventId() + ".xml";
		store(file, event.date(), name, name, event.eventId());
	}

	private void store(Path file, LocalDate date, String preferredName, String canonicalName, String identity) {

		Path directory = ArchiveLayout.directoryFor(request.archiveRoot(), date, request.organizeMode());

		try {
			switch (archive.store(file, directory, preferredName, canonicalName, identity)) {
				case COPIED -> counters.copied++;
				case COPIED_RENAMED -> {
					counters.copied++;
					counters.renamed++;
				}
				case REPLACED -> counters.replaced++;
				case ALREADY_EXISTS -> counters.alreadyExisting++;
			}
		} catch (IOException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			counters.copyFailures.add(new CopyFailure(file.getFileName().toString(), reason));
		}
	}

	static void throwIfCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Coleta cancelada");
		}
	}
}