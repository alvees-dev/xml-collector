package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;

public class XmlArchive implements DocumentArchive {

	private enum State {
		SAME_DOCUMENT,
		OTHER_DOCUMENT,
		BROKEN
	}

	private static final String TEMP_SUFFIX = ".part";
	private static final Set<DocumentModel> ALL_MODELS = EnumSet.allOf(DocumentModel.class);

	private final DocumentReader reader;

	public XmlArchive(DocumentReader reader) {
		this.reader = reader;
	}

	@Override
	public Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
			String canonicalName, String identity) throws IOException {

		Path preferred = directory.resolve(preferredName);

		if (!Files.exists(preferred)) {
			write(content, sourceModified, preferred);
			return Outcome.COPIED;
		}

		State preferredState = inspect(content, sourceModified, preferred, identity);

		if (preferredState == State.SAME_DOCUMENT) {
			return Outcome.ALREADY_EXISTS;
		}

		boolean hasCanonicalAlternative = !preferredName.equals(canonicalName);

		if (preferredState == State.BROKEN || !hasCanonicalAlternative) {
			write(content, sourceModified, preferred);
			return Outcome.REPLACED;
		}

		Path canonical = directory.resolve(canonicalName);

		if (!Files.exists(canonical)) {
			write(content, sourceModified, canonical);
			return Outcome.COPIED_RENAMED;
		}

		if (inspect(content, sourceModified, canonical, identity) == State.SAME_DOCUMENT) {
			return Outcome.ALREADY_EXISTS;
		}

		write(content, sourceModified, canonical);
		return Outcome.REPLACED;
	}

	@Override
	public void requireWritable(Path directory) throws IOException {

		Path existing = directory;

		// A folder that does not exist yet is created by the first file, so its nearest parent must accept writing.
		while (existing != null && !Files.isDirectory(existing)) {
			existing = existing.getParent();
		}

		if (existing == null) {
			throw new NoSuchFileException(directory.toString());
		}

		Files.delete(Files.createTempFile(existing, ".coletor-xml-", TEMP_SUFFIX));
	}

	private State inspect(byte[] content, FileTime sourceModified, Path existing, String identity)
			throws IOException {

		if (sameSizeAndTime(content, sourceModified, existing)) {
			return State.SAME_DOCUMENT;
		}

		Optional<String> found = identityOf(existing);

		if (found.isEmpty()) {
			return State.BROKEN;
		}

		return found.get().equals(identity) ? State.SAME_DOCUMENT : State.OTHER_DOCUMENT;
	}

	private Optional<String> identityOf(Path existing) {
		try {
			return reader.read(Files.readAllBytes(existing), ALL_MODELS).identity();
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static boolean sameSizeAndTime(byte[] content, FileTime sourceModified, Path existing)
			throws IOException {
		return content.length == Files.size(existing)
				&& sourceModified.equals(Files.getLastModifiedTime(existing));
	}

	private static void write(byte[] content, FileTime sourceModified, Path target) throws IOException {

		Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);

		try {
			writeTemp(content, temp);
			Files.setLastModifiedTime(temp, sourceModified);

			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}

		} catch (IOException e) {
			Files.deleteIfExists(temp);
			throw e;
		}
	}

	private static void writeTemp(byte[] content, Path temp) throws IOException {
		try {
			Files.write(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		} catch (NoSuchFileException e) {
			Files.createDirectories(temp.getParent());
			Files.write(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		}
	}
}