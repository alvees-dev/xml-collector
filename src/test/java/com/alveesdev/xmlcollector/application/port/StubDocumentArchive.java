package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface StubDocumentArchive extends DocumentArchive {

	@Override
	default void requireWritable(Path directory) throws IOException {
	}

	@Override
	default boolean holdsVoidingInAnotherMonth(Path archiveRoot, Path month, String fileName) throws IOException {
		return false;
	}

	@Override
	default Optional<Path> findDocument(Path month, String identity, String fileName) throws IOException {
		return Optional.empty();
	}
}
