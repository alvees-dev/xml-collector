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

	private record ScannedSource(int index, List<SourceFile> files) {
	}

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
		List<ScannedSource> scanned = scan(request, failures, listener, cancelled);

		int total = scanned.stream().mapToInt(source -> source.files().size()).sum();
		int[] processed = {0};

		listener.onProgress(new CollectionProgress(0, 0, total));

		ExecutorService executor = Executors.newFixedThreadPool(threads, new WorkerThreadFactory());

		try {
			SourceProcessor processor = new SourceProcessor(request, sourceFiles, reader, archive, executor);

			for (ScannedSource source : scanned) {
				processor.process(source.index(), source.files(), () -> {
					processed[0]++;
					listener.onProgress(new CollectionProgress(source.index(), processed[0], total));
				}, cancelled);
			}

			List<SourceResult> results = new ArrayList<>();

			for (ScannedSource source : scanned) {
				results.add(processor.result(source.index()));
			}

			String historyFailure = saveIssuedNumbers(processor);

			return new CollectionResult(results, processor.missingNumbers(), processor.summary(), failures, historyFailure);

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

	private List<ScannedSource> scan(CollectionRequest request, List<SourceFailure> failures,
			ProgressListener listener, BooleanSupplier cancelled) {

		List<ScannedSource> scanned = new ArrayList<>();
		List<String> directories = request.directories();

		for (int index = 0; index < directories.size(); index++) {
			SourceProcessor.throwIfCancelled(cancelled);
			listener.onProgress(new CollectionProgress(index, 0, -1));

			String directory = directories.get(index) == null ? "" : directories.get(index).trim();

			if (directory.isEmpty()) {
				failures.add(new SourceFailure(index, Reason.NOT_INFORMED, directory));
				scanned.add(new ScannedSource(index, List.of()));
				continue;
			}

			try {
				List<SourceFile> files = new ArrayList<>();

				for (Path folder : foldersOf(index, Path.of(directory), request, failures)) {
					ScanResult result = sourceFiles.scan(folder, request.archiveRoot());

					for (Path inaccessible : result.inaccessible()) {
						failures.add(new SourceFailure(index, Reason.SUBFOLDER_INACCESSIBLE, inaccessible.toString()));
					}

					files.addAll(result.files());
				}

				scanned.add(new ScannedSource(index, files));

			} catch (IOException | InvalidPathException e) {
				failures.add(new SourceFailure(index, Reason.INACCESSIBLE, directory));
				scanned.add(new ScannedSource(index, List.of()));
			}
		}

		return scanned;
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
			failures.add(new SourceFailure(index, Reason.NO_MODEL_FOLDER, root.toString()));
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