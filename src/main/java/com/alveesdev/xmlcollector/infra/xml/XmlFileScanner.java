package com.alveesdev.xmlcollector.infra.xml;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class XmlFileScanner {

	public record ScanResult(List<Path> files, List<Path> inaccessible) {
	}

	public ScanResult scan(Path directory, Path excludedRoot) throws IOException {

		if (!Files.isDirectory(directory)) {
			throw new NotDirectoryException(directory.toString());
		}

		Path excluded = excludedRoot.toAbsolutePath().normalize();
		List<Path> files = new ArrayList<>();
		List<Path> inaccessible = new ArrayList<>();

		Files.walkFileTree(directory, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return dir.toAbsolutePath().normalize().startsWith(excluded)
						? FileVisitResult.SKIP_SUBTREE
						: FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile() && isXml(file)) {
					files.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				if (file.equals(directory)) {
					throw exc;
				}
				inaccessible.add(file);
				return FileVisitResult.CONTINUE;
			}
		});

		return new ScanResult(files, inaccessible);
	}

	private static boolean isXml(Path file) {
		return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
	}
}