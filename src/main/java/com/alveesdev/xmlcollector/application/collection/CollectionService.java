package com.alveesdev.xmlcollector.application.collection;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.SourceFailure.Reason;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.Failures;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.SourceFiles.ResolvedFolders;
import com.alveesdev.xmlcollector.application.port.SourceFiles.ScanResult;
import com.alveesdev.xmlcollector.application.port.SourceFiles.SourceFile;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;

public class CollectionService {

	public static final int DEFAULT_THREADS = 4;

	private static final Logger LOG = Logger.getLogger(CollectionService.class.getName());

	private final SourceFiles sourceFiles;
	private final DocumentReader reader;
	private final DocumentArchive archive;
	private final IssuedNumberRepository issuedNumbers;
	private final int threads;

	public CollectionService(SourceFiles sourceFiles, DocumentReader reader, DocumentArchive archive,
			IssuedNumberRepository issuedNumbers) {
		this(sourceFiles, reader, archive, issuedNumbers, DEFAULT_THREADS);
	}

	public CollectionService(SourceFiles sourceFiles, DocumentReader reader, DocumentArchive archive,
			IssuedNumberRepository issuedNumbers, int threads) {
		if (threads < 1) {
			throw new IllegalArgumentException("threads must be at least 1");
		}

		this.sourceFiles = sourceFiles;
		this.reader = reader;
		this.archive = archive;
		this.issuedNumbers = issuedNumbers;
		this.threads = threads;
	}

	public Optional<Path> storedFileOf(Path archiveRoot, DocumentEntry entry) throws IOException {
		return archive.findDocument(ArchiveLayout.monthDirectory(archiveRoot, entry.invoice().date()), entry.invoice().key().value(), entry.fileName());
	}

	// The folder of one month inside a source, as its search model describes it, or empty when it does not exist.
	public Optional<Path> monthFolderOf(String root, List<String> patterns, YearMonth month) throws IOException {
		try {
			DateRange period = new DateRange(month.atDay(1), month.atEndOfMonth());
			return sourceFiles.resolveFolders(Path.of(root.trim()), patterns, period).folders().stream().findFirst();
		} catch (InvalidPathException e) {
			throw new IOException("Caminho inválido: " + root, e);
		}
	}

	public CollectionResult collect(CollectionRequest request, ProgressListener listener, BooleanSupplier cancelled) {
		return run(request, listener, cancelled, true);
	}

	// Works like a collection without copying anything. The numbers seen still go to the history, which keeps them from being voided.
	public CollectionResult read(CollectionRequest request, ProgressListener listener, BooleanSupplier cancelled) {
		return run(request, listener, cancelled, false);
	}

	private CollectionResult run(CollectionRequest request, ProgressListener listener, BooleanSupplier cancelled, boolean storing) {

		List<SourceFailure> failures = new ArrayList<>();
		int sources = request.directories().size();

		ExecutorService executor = Executors.newFixedThreadPool(threads, new WorkerThreadFactory());

		try {
			SourceProcessor processor = new SourceProcessor(request, sourceFiles, reader, archive, executor, storing);

			for (int index = 0; index < sources; index++) {
				SourceProcessor.throwIfCancelled(cancelled);

				if (request.target().source().test(index)) {
					collectSource(index, request, processor, failures, listener, cancelled);
				}
			}

			// Only now, because an event of a later source can cancel an invoice of an earlier one.
			List<SourceResult> results = new ArrayList<>();

			for (int index = 0; index < sources; index++) {
				results.add(processor.result(index));
			}

			Map<Integer, List<NumberRange>> gapsBeforeTheFirst = Map.of();
			String historyFailure = null;

			// The history only protects the voiding of NFCe, and the numbers of a purchase belong to other companies.
			if (request.category() == DocumentCategory.NFCE) {
				try {
					// A search only brings back the notes it wanted, so its first number says nothing about the series.
					if (request.target() == CollectionTarget.ALL) {
						gapsBeforeTheFirst = gapsBeforeTheFirst(processor);
					}

					issuedNumbers.saveAll(processor.issuedNumbers());

				} catch (IOException e) {
					LOG.log(Level.WARNING, "O histórico de números não pôde ser atualizado", e);
					historyFailure = Failures.describe(e);
				}
			}

			return new CollectionResult(results, processor.missingNumbers(gapsBeforeTheFirst), processor.summary(), failures,
					historyFailure, processor.cancelledKeys(), processor.voidedNumbers());

		} finally {
			executor.shutdownNow();
		}
	}

	// The last note of a month and the first of the next are only known to be missing when that next month is collected.
	private Map<Integer, List<NumberRange>> gapsBeforeTheFirst(SourceProcessor processor) throws IOException {

		Map<Integer, List<NumberRange>> gaps = new HashMap<>();

		for (IssuedNumber first : processor.firstIssuedNumbers()) {
			Optional<NumberRange> gap = issuedNumbers.lastBefore(first.cnpj(), first.modelCode(), first.series(), first.number())
					.flatMap(previous -> MissingNumberFinder.gapBetweenCollections(previous, first));

			gap.ifPresent(range -> gaps.computeIfAbsent(first.series(), series -> new ArrayList<>()).add(range));
		}

		return gaps;
	}

	private void collectSource(int index, CollectionRequest request, SourceProcessor processor, List<SourceFailure> failures,
			ProgressListener listener, BooleanSupplier cancelled) {

		listener.onProgress(CollectionProgress.counting(index));

		List<SourceFile> files = scan(index, request, failures);
		int total = files.size();
		int[] processed = {0};

		listener.onProgress(CollectionProgress.reading(index, 0, total));

		processor.process(index, files, () -> {
			processed[0]++;
			listener.onProgress(CollectionProgress.reading(index, processed[0], total));
		}, cancelled);

		listener.onProgress(finalProgress(index, total, failures));
	}

	private static CollectionProgress finalProgress(int index, int total, List<SourceFailure> failures) {

		List<SourceFailure> ofSource = failures.stream()
				.filter(failure -> failure.sourceIndex() == index)
				.toList();

		boolean failed = ofSource.stream()
				.anyMatch(failure -> failure.reason() != Reason.SUBFOLDER_INACCESSIBLE && failure.reason() != Reason.FOLDER_NOT_FOUND);

		if (failed) {
			return CollectionProgress.failed(index);
		}

		return ofSource.isEmpty() ? CollectionProgress.done(index, total) : CollectionProgress.doneWithWarning(index, total);
	}

	private List<SourceFile> scan(int index, CollectionRequest request, List<SourceFailure> failures) {

		String directory = request.directories().get(index) == null ? "" : request.directories().get(index).trim();

		if (directory.isEmpty()) {
			failures.add(new SourceFailure(index, Reason.NOT_INFORMED, directory));
			return List.of();
		}

		try {
			List<SourceFile> files = new ArrayList<>();

			for (Path folder : foldersOf(index, Path.of(directory), request, failures)) {
				ScanResult result = sourceFiles.scan(folder, request.archiveRoot());

				for (Path inaccessible : result.inaccessible()) {
					failures.add(new SourceFailure(index, Reason.SUBFOLDER_INACCESSIBLE, inaccessible.toString()));
				}

				result.files().stream()
						.filter(file -> request.target().fileName().test(file.path().getFileName().toString()))
						.forEach(files::add);
			}

			return files;

		} catch (IOException | InvalidPathException e) {
			failures.add(new SourceFailure(index, Reason.INACCESSIBLE, directory));
			return List.of();
		}
	}

	private List<Path> foldersOf(int index, Path root, CollectionRequest request, List<SourceFailure> failures) throws IOException {

		if (request.folderPatterns().isEmpty()) {
			return List.of(root);
		}

		ResolvedFolders resolved = sourceFiles.resolveFolders(root, request.folderPatterns(), request.period());

		for (Path inaccessible : resolved.inaccessible()) {
			failures.add(new SourceFailure(index, Reason.SUBFOLDER_INACCESSIBLE, inaccessible.toString()));
		}

		if (resolved.folders().isEmpty()) {
			String expected = resolved.missing().isEmpty() ? root.toString() : String.join(", ", resolved.missing());

			failures.add(new SourceFailure(index, Reason.NO_MODEL_FOLDER, expected));
			return resolved.folders();
		}

		for (String missing : resolved.missing()) {
			failures.add(new SourceFailure(index, Reason.FOLDER_NOT_FOUND, missing));
		}

		return resolved.folders();
	}

	private static class WorkerThreadFactory implements ThreadFactory {

		private final AtomicInteger counter = new AtomicInteger(1);

		@Override
		public Thread newThread(Runnable task) {
			Thread thread = new Thread(task, "xml-collector-worker-" + counter.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		}
	}
}