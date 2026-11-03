package com.alveesdev.xmlcollector.application.collection;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary.CopyFailure;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.application.port.SourceFiles.SourceFile;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.domain.service.CollectionRules;

class SourceProcessor {

	private static final int BATCH_SIZE = 400;

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

	private record StoreResult(DocumentArchive.Outcome outcome, CopyFailure failure) {
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
	private final SourceFiles sourceFiles;
	private final DocumentReader reader;
	private final DocumentArchive archive;
	private final ExecutorService executor;

	private final CollectionRules rules;
	private final Counters counters = new Counters();

	SourceProcessor(CollectionRequest request, SourceFiles sourceFiles, DocumentReader reader, DocumentArchive archive,
			ExecutorService executor) {
		this.request = request;
		this.sourceFiles = sourceFiles;
		this.reader = reader;
		this.archive = archive;
		this.executor = executor;
		this.rules = new CollectionRules(request.period());
	}

	void process(int sourceIndex, List<SourceFile> files, Runnable onFileDone, BooleanSupplier cancelled) {

		for (int start = 0; start < files.size(); start += BATCH_SIZE) {
			throwIfCancelled(cancelled);

			List<SourceFile> batch = files.subList(start, Math.min(start + BATCH_SIZE, files.size()));

			List<ReadFile> readFiles = readInParallel(batch, onFileDone, cancelled);
			List<StoreJob> jobs = decide(sourceIndex, readFiles);
			storeInParallel(jobs, cancelled);
		}
	}

	SourceResult result(int sourceIndex) {
		return new SourceResult(sourceIndex, rules.entriesOf(sourceIndex));
	}

	Set<IssuedNumber> issuedNumbers() {
		return rules.issuedNumbers();
	}

	SortedMap<Integer, List<NumberRange>> missingNumbers() {
		return rules.missingNumbers();
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
			byte[] content = sourceFiles.read(source);
			XmlDocument document = reader.read(content, request.category().models());

			boolean needsStoring = document instanceof XmlDocument.Invoice
					|| document instanceof XmlDocument.CancelEvent
					|| document instanceof XmlDocument.Voiding;

			return new ReadFile(source, fileName, document, needsStoring ? content : null);

		} catch (IOException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			return new ReadFile(source, fileName, new XmlDocument.Unreadable(reason), null);
		}
	}

	private List<StoreJob> decide(int sourceIndex, List<ReadFile> readFiles) {

		List<StoreJob> jobs = new ArrayList<>();

		for (ReadFile file : readFiles) {
			CollectionRules.Decision decision = rules.decide(sourceIndex, file.fileName(), file.document());

			if (decision instanceof CollectionRules.Store store) {
				jobs.add(job(file, store));
			} else if (decision instanceof CollectionRules.Skip skip) {
				count(skip.reason());
			}
		}

		return jobs;
	}

	private void count(CollectionRules.SkipReason reason) {
		switch (reason) {
			case DUPLICATE -> counters.duplicates++;
			case OUT_OF_PERIOD -> counters.outOfPeriod++;
			case IGNORED -> counters.ignored++;
			case UNREADABLE -> counters.errors++;
		}
	}

	private StoreJob job(ReadFile file, CollectionRules.Store store) {
		DocumentCategory category = store.voiding() ? DocumentCategory.VOIDED : request.category();
		Path directory = ArchiveLayout.directoryFor(request.archiveRoot(), store.date(), request.organizeMode(), category);

		return new StoreJob(file.content(), file.source().lastModified(), file.fileName(), directory,
				store.preferredName(), store.canonicalName(), store.identity());
	}

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

	private <T> List<Future<T>> submitAll(List<Callable<T>> tasks) {
		List<Future<T>> futures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			futures.add(executor.submit(task));
		}

		return futures;
	}

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