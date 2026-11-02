package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MonthZipArchiver {

	public record ZipResult(Path file, int entries) {
	}

	private static final String ZIP_SUFFIX = ".zip";
	private static final String TEMP_SUFFIX = ".part";

	public ZipResult zip(Path monthDirectory) throws IOException {

		String rootName = monthDirectory.getFileName().toString();
		Path zipFile = monthDirectory.resolveSibling(rootName + ZIP_SUFFIX);
		Path temp = monthDirectory.resolveSibling(rootName + ZIP_SUFFIX + TEMP_SUFFIX);
		int[] entries = {0};

		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {

			Files.walkFileTree(monthDirectory, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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

		return new ZipResult(zipFile, entries[0]);
	}
}