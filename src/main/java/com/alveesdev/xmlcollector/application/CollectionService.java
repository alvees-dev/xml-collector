package com.alveesdev.xmlcollector.application;

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

import com.alveesdev.xmlcollector.application.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.SourceFailure.Reason;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.xml.NfceXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner.ScanResult;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner.SourceFile;

public class CollectionService {

	public static final int DEFAULT_THREADS = 4;

	private record ScannedSource(int index, List<SourceFile> files) {
	}

	private final XmlFileScanner scanner;
	private final NfceXmlReader reader;
	private final XmlArchive archive;
	private final int threads;

	public CollectionService() {
		this(new XmlFileScanner(), new NfceXmlReader(), DEFAULT_THREADS);
	}

	public CollectionService(XmlFileScanner scanner, NfceXmlReader reader) {
		this(scanner, reader, DEFAULT_THREADS);
	}

	public CollectionService(XmlFileScanner scanner, NfceXmlReader reader, int threads) {
		if (threads < 1) {
			throw new IllegalArgumentException("threads must be at least 1");
		}

		this.scanner = scanner;
		this.reader = reader;
		this.archive = new XmlArchive(reader);
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
			SourceProcessor processor = new SourceProcessor(request, reader, archive, executor);
			List<SourceResult> results = new ArrayList<>();

			for (ScannedSource source : scanned) {
				results.add(processor.process(source.index(), source.files(), () -> {
					processed[0]++;
					listener.onProgress(new CollectionProgress(source.index(), processed[0], total));
				}, cancelled));
			}

			return new CollectionResult(results, processor.summary(), failures);

		} finally {
			executor.shutdownNow();
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
				ScanResult result = scanner.scan(Path.of(directory), request.archiveRoot());

				for (Path inaccessible : result.inaccessible()) {
					failures.add(new SourceFailure(index, Reason.SUBFOLDER_INACCESSIBLE, inaccessible.toString()));
				}

				scanned.add(new ScannedSource(index, result.files()));

			} catch (IOException | InvalidPathException e) {
				failures.add(new SourceFailure(index, Reason.INACCESSIBLE, directory));
				scanned.add(new ScannedSource(index, List.of()));
			}
		}

		return scanned;
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