package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import com.alveesdev.xmlcollector.infra.xml.NfceXmlReader;

/**
 * Stores XML content in the archive. Instances are stateless and safe to use from several threads, as long as two
 * threads never store to the same target file at the same time (the caller is responsible for that).
 */
public class XmlArchive {

	public enum Outcome {
		COPIED,
		COPIED_RENAMED,
		REPLACED,
		ALREADY_EXISTS
	}

	private enum State {
		SAME_DOCUMENT,
		OTHER_DOCUMENT,
		BROKEN
	}

	private static final String TEMP_SUFFIX = ".part";

	private final NfceXmlReader reader;

	public XmlArchive(NfceXmlReader reader) {
		this.reader = reader;
	}

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

	private State inspect(byte[] content, FileTime sourceModified, Path existing, String identity)
			throws IOException {

		if (sameSizeAndTime(content, sourceModified, existing)) {
			return State.SAME_DOCUMENT;
		}

		Optional<String> found = reader.read(existing).identity();

		if (found.isEmpty()) {
			return State.BROKEN;
		}

		return found.get().equals(identity) ? State.SAME_DOCUMENT : State.OTHER_DOCUMENT;
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
			// The target directory does not exist yet: create it only now, instead of once per file.
			Files.createDirectories(temp.getParent());
			Files.write(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		}
	}
}