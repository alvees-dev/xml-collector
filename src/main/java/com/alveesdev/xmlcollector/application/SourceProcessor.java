package com.alveesdev.xmlcollector.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner.SourceFile;

/**
 * Processes the files of one source in batches. Each batch runs in three phases:
 * <ol>
 * <li>read and parse every file in parallel (network bound, no shared state);</li>
 * <li>decide, sequentially and in file order, what to do with each document (duplicates, period, counters);</li>
 * <li>store the accepted documents in parallel, never running two jobs that touch the same target file at once.</li>
 * </ol>
 * Only the batch being processed is kept in memory.
 */
class SourceProcessor {

	private static final int BATCH_SIZE = 400;

	private record Pending(String fileName, XmlDocument.Nfce nfce) {
	}

	private record ReadFile(SourceFile source, String fileName, XmlDocument document, byte[] content) {
	}

	private record StoreJob(
			byte[] content,
			FileTime sourceModified,
			String sourceName,
			Path directory,
			String preferredName,
			String canonicalName,
			String identity) {

		String preferredKey() {
			return key(directory.resolve(preferredName));
		}

		String canonicalKey() {
			return key(directory.resolve(canonicalName));
		}

		private static String key(Path path) {
			return path.toString().toLowerCase(Locale.ROOT);
		}
	}

	private record StoreResult(XmlArchive.Outcome outcome, CopyFailure failure) {
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
	private final ExecutorService executor;

	private final Counters counters = new Counters();
	private final Set<AccessKey> seenKeys = new HashSet<>();
	private final Set<String> seenEventIds = new HashSet<>();

	SourceProcessor(CollectionRequest request, NfceXmlReader reader, XmlArchive archive, ExecutorService executor) {
		this.request = request;
		this.reader = reader;
		this.archive = archive;
		this.executor = executor;
	}

	SourceResult process(int sourceIndex, List<SourceFile> files, Runnable onFileDone, BooleanSupplier cancelled) {

		Set<AccessKey> cancelledKeys = new HashSet<>();
		List<Pending> pending = new ArrayList<>();

		for (int start = 0; start < files.size(); start += BATCH_SIZE) {
			throwIfCancelled(cancelled);

			List<SourceFile> batch = files.subList(start, Math.min(start + BATCH_SIZE, files.size()));

			List<ReadFile> readFiles = readInParallel(batch, onFileDone, cancelled);
			List<StoreJob> jobs = decide(readFiles, cancelledKeys, pending);
			storeInParallel(jobs, cancelled);
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

	// Phase 1: parallel read and parse

	private List<ReadFile> readInParallel(List<SourceFile> batch, Runnable onFileDone, BooleanSupplier cancelled) {

		List<Callable<ReadFile>> tasks = new ArrayList<>();

		for (SourceFile source : batch) {
			tasks.add(() -> {
				throwIfCancelled(cancelled);
				return read(source);
			});
		}

		List<ReadFile> results = new ArrayList<>();

		awaitAll(submitAll(tasks), result -> {
			results.add(result);
			onFileDone.run();
		});

		return results;
	}

	private ReadFile read(SourceFile source) {

		String fileName = source.path().getFileName().toString();

		try {
			byte[] content = Files.readAllBytes(source.path());
			XmlDocument document = reader.read(content);

			boolean needsStoring = document instanceof XmlDocument.Nfce || document instanceof XmlDocument.CancelEvent;

			return new ReadFile(source, fileName, document, needsStoring ? content : null);

		} catch (IOException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			return new ReadFile(source, fileName, new XmlDocument.Unreadable(reason), null);
		}
	}

	// Phase 2: sequential decisions, in file order

	private List<StoreJob> decide(List<ReadFile> readFiles, Set<AccessKey> cancelledKeys, List<Pending> pending) {

		List<StoreJob> jobs = new ArrayList<>();

		for (ReadFile file : readFiles) {
			XmlDocument document = file.document();

			if (document instanceof XmlDocument.Nfce nfce) {
				handleInvoice(file, nfce, pending, jobs);
			} else if (document instanceof XmlDocument.CancelEvent event) {
				handleCancelEvent(file, event, cancelledKeys, jobs);
			} else if (document instanceof XmlDocument.Ignored) {
				counters.ignored++;
			} else {
				counters.errors++;
				pending.add(new Pending(file.fileName(), null));
			}
		}

		return jobs;
	}

	private void handleInvoice(ReadFile file, XmlDocument.Nfce nfce, List<Pending> pending, List<StoreJob> jobs) {

		if (!seenKeys.add(nfce.key())) {
			counters.duplicates++;
			return;
		}

		if (!request.period().contains(nfce.date())) {
			counters.outOfPeriod++;
			return;
		}

		pending.add(new Pending(file.fileName(), nfce));

		String key = nfce.key().value();
		jobs.add(job(file, nfce.date(), file.fileName(), key + ".xml", key));
	}

	private void handleCancelEvent(ReadFile file, XmlDocument.CancelEvent event, Set<AccessKey> cancelledKeys,
			List<StoreJob> jobs) {

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
		jobs.add(job(file, event.date(), name, name, event.eventId()));
	}

	private StoreJob job(ReadFile file, LocalDate date, String preferredName, String canonicalName, String identity) {
		Path directory = ArchiveLayout.directoryFor(request.archiveRoot(), date, request.organizeMode());

		return new StoreJob(file.content(), file.source().lastModified(), file.fileName(), directory, preferredName,
				canonicalName, identity);
	}

	// Phase 3: parallel store

	private void storeInParallel(List<StoreJob> jobs, BooleanSupplier cancelled) {

		StoreResult[] results = new StoreResult[jobs.size()];
		List<Callable<Void>> tasks = new ArrayList<>();

		for (List<Integer> group : groupByTargetFile(jobs)) {
			tasks.add(() -> {
				for (int index : group) {
					throwIfCancelled(cancelled);
					results[index] = store(jobs.get(index));
				}
				return null;
			});
		}

		awaitAll(submitAll(tasks), ignored -> {
		});

		for (StoreResult result : results) {
			if (result.failure() != null) {
				counters.copyFailures.add(result.failure());
				continue;
			}

			switch (result.outcome()) {
				case COPIED -> counters.copied++;
				case COPIED_RENAMED -> {
					counters.copied++;
					counters.renamed++;
				}
				case REPLACED -> counters.replaced++;
				case ALREADY_EXISTS -> counters.alreadyExisting++;
			}
		}
	}

	private StoreResult store(StoreJob job) {
		try {
			return new StoreResult(
					archive.store(job.content(), job.sourceModified(), job.directory(), job.preferredName(),
							job.canonicalName(), job.identity()),
					null);
		} catch (IOException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			return new StoreResult(null, new CopyFailure(job.sourceName(), reason));
		}
	}

	/**
	 * Groups jobs that may touch the same target file (by preferred or canonical name), so each group can run
	 * sequentially, in file order, while different groups run in parallel. This keeps the result identical to a fully
	 * sequential run even when two different documents share a file name.
	 */
	private static List<List<Integer>> groupByTargetFile(List<StoreJob> jobs) {

		int[] parent = new int[jobs.size()];
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}

		Map<String, Integer> owner = new HashMap<>();

		for (int i = 0; i < jobs.size(); i++) {
			for (String key : List.of(jobs.get(i).preferredKey(), jobs.get(i).canonicalKey())) {
				Integer previous = owner.putIfAbsent(key, i);

				if (previous != null) {
					parent[find(parent, i)] = find(parent, previous);
				}
			}
		}

		Map<Integer, List<Integer>> groups = new LinkedHashMap<>();

		for (int i = 0; i < jobs.size(); i++) {
			groups.computeIfAbsent(find(parent, i), root -> new ArrayList<>()).add(i);
		}

		return new ArrayList<>(groups.values());
	}

	private static int find(int[] parent, int index) {
		while (parent[index] != index) {
			parent[index] = parent[parent[index]];
			index = parent[index];
		}
		return index;
	}

	// Executor helpers

	private <T> List<Future<T>> submitAll(List<Callable<T>> tasks) {
		List<Future<T>> futures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			futures.add(executor.submit(task));
		}

		return futures;
	}

	/** Waits for the futures in submission order, and cancels the remaining ones if anything goes wrong. */
	private static <T> void awaitAll(List<Future<T>> futures, Consumer<T> onResult) {
		try {
			for (Future<T> future : futures) {
				onResult.accept(future.get());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CancellationException("Coleta cancelada");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();

			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		} finally {
			for (Future<T> future : futures) {
				future.cancel(true);
			}
		}
	}

	static void throwIfCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Coleta cancelada");
		}
	}
}