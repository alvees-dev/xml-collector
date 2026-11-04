package com.alveesdev.xmlcollector.application.port;

import java.nio.file.Path;
import java.util.List;

public interface CloudStorage {

	// A previous file that could not be removed does not undo the upload, so it comes back as a failure message.
	record UploadResult(String link, String cleanupFailure) {
	}

	UploadResult replaceInSharedFolder(String accountEmail, String folderName, List<Path> files) throws DispatchException;
}
