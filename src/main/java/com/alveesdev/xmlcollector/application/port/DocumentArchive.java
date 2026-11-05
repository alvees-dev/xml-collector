package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

public interface DocumentArchive {

	enum Outcome {
		COPIED,
		COPIED_RENAMED,
		REPLACED,
		ALREADY_EXISTS
	}

	Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
			String canonicalName, String identity) throws IOException;

	void requireWritable(Path directory) throws IOException;

	boolean holdsVoidingInAnotherMonth(Path archiveRoot, Path month, String fileName) throws IOException;

	Optional<Path> findDocument(Path month, String identity, String fileName) throws IOException;
}
