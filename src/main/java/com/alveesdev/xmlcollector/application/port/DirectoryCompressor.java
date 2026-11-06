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

	// The files go inside a folder named rootName, and the zip is written apart from the directory it reads.
	Optional<CompressedFile> compress(Path directory, Path zipFile, String rootName, List<ExtraFile> extraFiles) throws IOException;

	// The newest zip in the folder whose name ends with the label, whatever CNPJs it names.
	Optional<CompressedFile> existing(Path zipFolder, String zipLabel) throws IOException;
}
