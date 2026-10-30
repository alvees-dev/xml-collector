package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import com.alveesdev.xmlcollector.infra.xml.NfceXmlReader;

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

	public Outcome store(Path source, Path directory, String preferredName, String canonicalName, String identity)
			throws IOException {

		Files.createDirectories(directory);

		Path preferred = directory.resolve(preferredName);

		if (!Files.exists(preferred)) {
			copyAtomically(source, preferred);
			return Outcome.COPIED;
		}

		State preferredState = inspect(source, preferred, identity);

		if (preferredState == State.SAME_DOCUMENT) {
			return Outcome.ALREADY_EXISTS;
		}

		boolean hasCanonicalAlternative = !preferredName.equals(canonicalName);

		if (preferredState == State.BROKEN || !hasCanonicalAlternative) {
			copyAtomically(source, preferred);
			return Outcome.REPLACED;
		}

		Path canonical = directory.resolve(canonicalName);

		if (!Files.exists(canonical)) {
			copyAtomically(source, canonical);
			return Outcome.COPIED_RENAMED;
		}

		if (inspect(source, canonical, identity) == State.SAME_DOCUMENT) {
			return Outcome.ALREADY_EXISTS;
		}

		copyAtomically(source, canonical);
		return Outcome.REPLACED;
	}

	private State inspect(Path source, Path existing, String identity) throws IOException {

		if (sameSizeAndTime(source, existing)) {
			return State.SAME_DOCUMENT;
		}

		Optional<String> found = reader.read(existing).identity();

		if (found.isEmpty()) {
			return State.BROKEN;
		}

		return found.get().equals(identity) ? State.SAME_DOCUMENT : State.OTHER_DOCUMENT;
	}

	private static boolean sameSizeAndTime(Path source, Path existing) throws IOException {
		return Files.size(source) == Files.size(existing)
				&& Files.getLastModifiedTime(source).equals(Files.getLastModifiedTime(existing));
	}

	private static void copyAtomically(Path source, Path target) throws IOException {

		Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);

		try {
			Files.copy(source, temp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);

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
}