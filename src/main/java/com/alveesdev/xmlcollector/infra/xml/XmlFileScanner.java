package com.alveesdev.xmlcollector.infra.xml;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.domain.model.DateRange;

public class XmlFileScanner implements SourceFiles {

	@Override
	public byte[] read(SourceFile file) throws IOException {
		return Files.readAllBytes(file.path());
	}

	@Override
	public void requireReadable(Path directory) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			stream.iterator().hasNext();
		} catch (DirectoryIteratorException e) {
			throw e.getCause();
		}
	}

	@Override
	public ResolvedFolders resolveFolders(Path root, List<String> patterns, DateRange period) throws IOException {
		return SearchFolderResolver.resolve(root, patterns, period);
	}

	@Override
	public ScanResult scan(Path directory, Path excludedRoot) throws IOException {

		if (!Files.isDirectory(directory)) {
			throw new NotDirectoryException(directory.toString());
		}

		Path excluded = excludedRoot.toAbsolutePath().normalize();

		// The archive is left out only when the search passes through it; a directory chosen inside it is read like any other.
		boolean insideTheArchive = directory.toAbsolutePath().normalize().startsWith(excluded);
		List<SourceFile> files = new ArrayList<>();
		List<Path> inaccessible = new ArrayList<>();

		Files.walkFileTree(directory, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return !insideTheArchive && dir.toAbsolutePath().normalize().startsWith(excluded)
						? FileVisitResult.SKIP_SUBTREE
						: FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile() && isXml(file)) {
					files.add(new SourceFile(file, attrs.lastModifiedTime()));
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