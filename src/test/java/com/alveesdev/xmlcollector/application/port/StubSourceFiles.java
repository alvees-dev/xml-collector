package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;

public interface StubSourceFiles extends SourceFiles {

	@Override
	default void requireReadable(Path directory) throws IOException {
	}
}
