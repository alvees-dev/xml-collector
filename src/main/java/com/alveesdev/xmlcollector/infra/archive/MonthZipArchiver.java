package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

public class MonthZipArchiver implements DirectoryCompressor {

	private static final String TEMP_SUFFIX = ".part";

	@Override
	public Optional<CompressedFile> existing(Path zipFolder, String zipLabel) throws IOException {

		if (!Files.isDirectory(zipFolder)) {
			return Optional.empty();
		}

		try (Stream<Path> files = Files.list(zipFolder)) {
			Optional<Path> newest = files
					.filter(Files::isRegularFile)
					.filter(file -> ArchiveLayout.isZipOf(file.getFileName().toString(), zipLabel))
					.max(Comparator.comparing(MonthZipArchiver::modifiedTime));

			return newest.isPresent() ? Optional.of(new CompressedFile(newest.get(), Files.size(newest.get()))) : Optional.empty();

		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static FileTime modifiedTime(Path file) {
		try {
			return Files.getLastModifiedTime(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Optional<CompressedFile> compress(Path monthDirectory, Path zipFile, String rootName, List<ExtraFile> extraFiles) throws IOException {

		Path zipFolder = zipFile.toAbsolutePath().getParent();
		Files.createDirectories(zipFolder);

		// Its own temporary file, because a cancelled compression can still be ending when the next one starts.
		Path temp = Files.createTempFile(zipFolder, zipFile.getFileName() + ".", TEMP_SUFFIX);
		int[] entries = {0};

		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {

			for (ExtraFile extra : extraFiles) {
				output.putNextEntry(new ZipEntry(rootName + "/" + extra.name()));
				output.write(extra.content());
				output.closeEntry();
			}

			Files.walkFileTree(monthDirectory, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedIOException("Compactação cancelada");
					}

					if (attrs.isRegularFile() && !file.getFileName().toString().endsWith(TEMP_SUFFIX)) {
						String relative = monthDirectory.relativize(file).toString().replace('\\', '/');

						ZipEntry entry = new ZipEntry(rootName + "/" + relative);
						entry.setLastModifiedTime(attrs.lastModifiedTime());

						output.putNextEntry(entry);
						Files.copy(file, output);
						output.closeEntry();

						entries[0]++;
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException e) {
			Files.deleteIfExists(temp);
			throw e;
		}

		try {
			Files.move(temp, zipFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, zipFile, StandardCopyOption.REPLACE_EXISTING);
		}

		if (entries[0] == 0) {
			Files.deleteIfExists(zipFile);
			return Optional.empty();
		}

		return Optional.of(new CompressedFile(zipFile, Files.size(zipFile)));
	}
}
