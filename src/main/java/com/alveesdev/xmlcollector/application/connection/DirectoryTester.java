package com.alveesdev.xmlcollector.application.connection;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult.Outcome;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.application.port.SourceFiles.ResolvedFolders;
import com.alveesdev.xmlcollector.domain.model.DateRange;

public class DirectoryTester {

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	private final SourceFiles sourceFiles;
	private final DocumentArchive archive;
	private final Clock clock;
	private final Duration timeout;

	public DirectoryTester(SourceFiles sourceFiles, DocumentArchive archive, Clock clock, Duration timeout) {
		this.sourceFiles = sourceFiles;
		this.archive = archive;
		this.clock = clock;
		this.timeout = timeout;
	}

	public DirectoryTestResult test(DirectoryTarget target) {

		if (target.directory().isBlank()) {
			return DirectoryTestResult.of(Outcome.NOT_INFORMED);
		}

		AtomicBoolean connected = new AtomicBoolean();
		FutureTask<DirectoryTestResult> task = new FutureTask<>(() -> check(target, connected));

		// Windows can take long to give up on an unreachable path, and that call cannot be interrupted.
		Thread worker = new Thread(task, "directory-test");
		worker.setDaemon(true);
		worker.start();

		try {
			return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			task.cancel(true);
			return DirectoryTestResult.of(connected.get() ? Outcome.SEARCH_TOO_SLOW : Outcome.NO_CONNECTION);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return DirectoryTestResult.of(Outcome.NO_CONNECTION);
		} catch (ExecutionException e) {
			throw new IllegalStateException(e.getCause());
		}
	}

	private DirectoryTestResult check(DirectoryTarget target, AtomicBoolean connected) {

		Path root;

		try {
			root = Path.of(target.directory().trim());
			sourceFiles.requireReadable(root);
		} catch (AccessDeniedException e) {
			return DirectoryTestResult.of(Outcome.NO_PERMISSION);
		} catch (IOException | InvalidPathException e) {
			return DirectoryTestResult.of(Outcome.NO_CONNECTION);
		}

		connected.set(true);

		boolean writable = canWrite(root, target.writableFolder());

		if (target.folderPatterns().isEmpty()) {
			return new DirectoryTestResult(Outcome.CONNECTED, true, List.of(), writable);
		}

		try {
			YearMonth previousMonth = YearMonth.now(clock).minusMonths(1);
			ResolvedFolders resolved = sourceFiles.resolveFolders(root, target.folderPatterns(),
					new DateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth()));

			List<String> missing = resolved.missing().stream()
					.map(folder -> folder.replace(root + File.separator, ""))
					.toList();

			return new DirectoryTestResult(Outcome.CONNECTED, !resolved.folders().isEmpty(), missing, writable);

		} catch (IOException e) {
			return DirectoryTestResult.of(Outcome.NO_CONNECTION);
		}
	}

	private boolean canWrite(Path root, String writableFolder) {

		if (writableFolder == null) {
			return true;
		}

		try {
			archive.requireWritable(root.resolve(writableFolder));
			return true;
		} catch (IOException | InvalidPathException e) {
			return false;
		}
	}
}
