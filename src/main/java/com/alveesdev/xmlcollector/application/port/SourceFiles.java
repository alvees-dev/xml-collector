package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

public interface SourceFiles {

	record SourceFile(Path path, FileTime lastModified) {
	}

	record ScanResult(List<SourceFile> files, List<Path> inaccessible) {
	}

	ScanResult scan(Path directory, Path excludedRoot) throws IOException;

	byte[] read(SourceFile file) throws IOException;
}
