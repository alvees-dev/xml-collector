package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.DateRange;

public interface SourceFiles {

	record SourceFile(Path path, FileTime lastModified) {
	}

	record ScanResult(List<SourceFile> files, List<Path> inaccessible) {
	}

	record ResolvedFolders(List<Path> folders, List<Path> inaccessible, List<String> missing) {
	}

	ResolvedFolders resolveFolders(Path root, List<String> patterns, DateRange period) throws IOException;

	ScanResult scan(Path directory, Path excludedRoot) throws IOException;

	byte[] read(SourceFile file) throws IOException;
}
