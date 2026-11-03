package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface DirectoryCompressor {

	record CompressedFile(Path file, long bytes) {
	}

	Optional<CompressedFile> compress(Path directory, String zipName) throws IOException;
}
