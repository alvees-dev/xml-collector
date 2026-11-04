package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface DirectoryCompressor {

	record CompressedFile(Path file, long bytes) {
	}

	// Goes inside the zip without existing in the directory, and never makes an empty directory worth a zip.
	record ExtraFile(String name, byte[] content) {
	}

	Optional<CompressedFile> compress(Path directory, String zipName, List<ExtraFile> extraFiles) throws IOException;

	default Optional<CompressedFile> existing(Path directory) throws IOException {
		return Optional.empty();
	}
}
