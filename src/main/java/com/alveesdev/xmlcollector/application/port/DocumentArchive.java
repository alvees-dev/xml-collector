package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public interface DocumentArchive {

	enum Outcome {
		COPIED,
		COPIED_RENAMED,
		REPLACED,
		ALREADY_EXISTS
	}

	Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
			String canonicalName, String identity) throws IOException;
}
