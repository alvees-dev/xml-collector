package com.alveesdev.xmlcollector.application.collection;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.SourceFailure.Reason;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.application.port.SourceFiles.ResolvedFolders;
import com.alveesdev.xmlcollector.application.port.SourceFiles.ScanResult;
import com.alveesdev.xmlcollector.application.port.SourceFiles.SourceFile;

public class CollectionService {

	public static final int DEFAULT_THREADS = 4;

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

	public CollectionResult collect(CollectionRequest request) {
		return collect(request, ProgressListener.NONE, () -> false);
	}

	public CollectionResult collect(CollectionRequest request, ProgressListener listener, BooleanSupplier cancelled) {

		List<SourceFailure> failures = new ArrayList<>();
		int sources = request.directories().size();

		ExecutorService executor = Executors.newFixedThreadPool(threads, new WorkerThreadFactory());

		try {
			SourceProcessor processor = new SourceProcessor(request, sourceFiles, reader, archive, executor);

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

			String historyFailure = saveIssuedNumbers(processor);

			return new CollectionResult(
					results, processor.missingNumbers(), processor.summary(), failures, historyFailure, processor.cancelledKeys());

		} finally {
			executor.shutdownNow();
		}
	}

	private String saveIssuedNumbers(SourceProcessor processor) {
		try {
			issuedNumbers.saveAll(processor.issuedNumbers());
			return null;
		} catch (IOException e) {
			return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		}
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

		return CollectionProgress.done(index, total);
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