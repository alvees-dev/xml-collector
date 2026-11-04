package com.alveesdev.xmlcollector;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

// On Windows an antivirus or the indexer can hold a new file open for a moment. The file is already deleted, but its
// folder still counts as not empty, and JUnit tries only once. A file really left open by the code still fails the test,
// because that one never lets go during the attempts below.
public class RetryingTempDirFactory implements TempDirFactory {

	private static final int ATTEMPTS = 30;
	private static final long WAIT_MILLIS = 100;

	private final List<Path> directories = new ArrayList<>();

	@Override
	public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext) throws IOException {
		Path directory = Files.createTempDirectory("junit-");
		directories.add(directory);
		return directory;
	}

	@Override
	public void close() throws IOException {
		for (Path directory : directories) {
			deleteWithRetries(directory);
		}
	}

	private static void deleteWithRetries(Path directory) throws IOException {

		for (int attempt = 1; ; attempt++) {
			try {
				delete(directory);
				return;
			} catch (FileSystemException e) {
				if (attempt == ATTEMPTS) {
					throw e;
				}

				pause();
			}
		}
	}

	private static void delete(Path directory) throws IOException {

		if (!Files.exists(directory)) {
			return;
		}

		Files.walkFileTree(directory, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				deleteIfThere(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path folder, IOException failure) throws IOException {
				if (failure != null) {
					throw failure;
				}

				deleteIfThere(folder);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteIfThere(Path path) throws IOException {
		try {
			Files.delete(path);
		} catch (NoSuchFileException e) {
			// Something else finished removing it between the attempts.
		}
	}

	private static void pause() throws IOException {
		try {
			Thread.sleep(WAIT_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while deleting a temp directory", e);
		}
	}
}
