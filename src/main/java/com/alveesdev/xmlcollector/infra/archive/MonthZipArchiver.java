package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

public class MonthZipArchiver implements DirectoryCompressor {

	private static final String ZIP_SUFFIX = ".zip";
	private static final String TEMP_SUFFIX = ".part";

	@Override
	public Optional<CompressedFile> compress(Path monthDirectory, String zipName) throws IOException {

		String rootName = ArchiveLayout.monthLabel(monthDirectory);
		Path zipFile = monthDirectory.resolveSibling(zipName + ZIP_SUFFIX);
		Path temp = monthDirectory.resolveSibling(zipName + ZIP_SUFFIX + TEMP_SUFFIX);
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

		if (entries[0] == 0) {
			Files.deleteIfExists(zipFile);
			return Optional.empty();
		}

		return Optional.of(new CompressedFile(zipFile, Files.size(zipFile)));
	}
}
