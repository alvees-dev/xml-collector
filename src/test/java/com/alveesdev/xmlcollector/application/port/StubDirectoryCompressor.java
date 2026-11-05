package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface StubDirectoryCompressor extends DirectoryCompressor {

	@Override
	default Optional<CompressedFile> existing(Path directory) throws IOException {
		return Optional.empty();
	}
}
