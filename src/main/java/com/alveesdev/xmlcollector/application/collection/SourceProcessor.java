package com.alveesdev.xmlcollector.application.collection;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary.CopyFailure;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.Failures;
import com.alveesdev.xmlcollector.application.port.SourceFiles.SourceFile;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.domain.service.CollectionRules;

class SourceProcessor {

	private static final Logger LOG = Logger.getLogger(SourceProcessor.class.getName());
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

		List<String> targets() {
			return List.of(key(directory.resolve(preferredName)), key(directory.resolve(canonicalName)));
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
	private final ParallelTasks parallel;

	private final CollectionRules rules;
	private final Counters counters = new Counters();

	SourceProcessor(CollectionRequest request, SourceFiles sourceFiles, DocumentReader reader, DocumentArchive archive,
			ExecutorService executor) {
		this.request = request;
		this.sourceFiles = sourceFiles;
		this.reader = reader;
		this.archive = archive;
		this.parallel = new ParallelTasks(executor);
		this.rules = new CollectionRules(request.period(), request.target().invoice());
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

	Set<AccessKey> cancelledKeys() {
		return rules.cancelledKeys();
	}

	List<IssuedNumber> firstIssuedNumbers() {
		return rules.firstIssuedNumbers();
	}

	SortedMap<Integer, List<NumberRange>> missingNumbers(Map<Integer, List<NumberRange>> gapsBeforeTheFirst) {
		return rules.missingNumbers(gapsBeforeTheFirst);
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

		parallel.runAll(tasks, result -> {
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

			boolean needsStoring = switch (document) {
				case XmlDocument.Invoice _, XmlDocument.CancelEvent _, XmlDocument.Voiding _ -> true;
				case XmlDocument.Ignored _, XmlDocument.Unreadable _ -> false;
			};

			return new ReadFile(source, fileName, document, needsStoring ? content : null);

		} catch (IOException e) {
			String reason = Failures.describe(e);
			return new ReadFile(source, fileName, new XmlDocument.Unreadable(reason), null);
		}
	}

	private List<StoreJob> decide(int sourceIndex, List<ReadFile> readFiles) {

		List<StoreJob> jobs = new ArrayList<>();

		for (ReadFile file : readFiles) {
			logIfLeftOut(file);

			CollectionRules.Decision decision = rules.decide(sourceIndex, file.fileName(), file.document());

			switch (decision) {
				case CollectionRules.Store store -> {
					// A gap voided after its collection stays in the collected month, even with a date of the next one.
					if (heldInAnotherMonth(store)) {
						counters.alreadyExisting++;
					} else {
						jobs.add(job(sourceIndex, file, store));
					}
				}
				case CollectionRules.Skip skip -> count(skip.reason());
			}
		}

		return jobs;
	}

	// The screen only counts these files, so this is the one place that says which file it was and why.
	private static void logIfLeftOut(ReadFile file) {
		switch (file.document()) {
			case XmlDocument.Unreadable unreadable -> LOG.warning("Unreadable file " + file.source().path() + ": " + unreadable.reason());
			case XmlDocument.Ignored ignored -> LOG.info("Ignored file " + file.source().path() + ": " + ignored.reason());
			case XmlDocument.Invoice _, XmlDocument.CancelEvent _, XmlDocument.Voiding _ -> {
			}
		}
	}

	private void count(CollectionRules.SkipReason reason) {
		switch (reason) {
			case DUPLICATE -> counters.duplicates++;
			case OUT_OF_PERIOD -> counters.outOfPeriod++;
			case IGNORED -> counters.ignored++;
			case UNREADABLE -> counters.errors++;
		}
	}

	private StoreJob job(int sourceIndex, ReadFile file, CollectionRules.Store store) {
		Path directory = directoryOf(sourceIndex, store);

		return new StoreJob(file.content(), file.source().lastModified(), file.fileName(), directory,
				store.preferredName(), store.canonicalName(), store.identity());
	}

	private Path directoryOf(int sourceIndex, CollectionRules.Store store) {

		// A voiding has no day folder, whatever the organization of the invoices is.
		if (request.separationMode() == SeparationMode.BY_PDV) {
			OrganizeMode mode = store.voiding() ? OrganizeMode.SINGLE_FOLDER : request.organizeMode();
			return ArchiveLayout.pdvDirectoryFor(request.archiveRoot(), store.date(), mode, sourceIndex);
		}

		if (store.voiding()) {
			return ArchiveLayout.voidingDirectoryFor(request.archiveRoot(), store.date());
		}

		return ArchiveLayout.directoryFor(request.archiveRoot(), store.date(), request.organizeMode(), request.category());
	}

	private boolean heldInAnotherMonth(CollectionRules.Store store) {

		if (!store.voiding()) {
			return false;
		}

		try {
			return archive.holdsVoidingInAnotherMonth(
					request.archiveRoot(), ArchiveLayout.monthDirectory(request.archiveRoot(), store.date()), store.preferredName());
		} catch (IOException e) {
			// A repeated file is better than a missing one.
			return false;
		}
	}

	private void storeInParallel(List<StoreJob> jobs, BooleanSupplier cancelled) {

		StoreResult[] results = new StoreResult[jobs.size()];
		List<Callable<Void>> tasks = new ArrayList<>();

		for (List<Integer> group : TargetFileGroups.of(jobs.stream().map(StoreJob::targets).toList())) {
			tasks.add(() -> {
				for (int index : group) {
					throwIfCancelled(cancelled);
					results[index] = store(jobs.get(index));
				}
				return null;
			});
		}

		parallel.runAll(tasks, ignored -> {
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
			String reason = Failures.describe(e);
			LOG.log(Level.WARNING, "File not stored: " + job.sourceName(), e);
			return new StoreResult(null, new CopyFailure(job.sourceName(), reason));
		}
	}

	static void throwIfCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Coleta cancelada");
		}
	}
}