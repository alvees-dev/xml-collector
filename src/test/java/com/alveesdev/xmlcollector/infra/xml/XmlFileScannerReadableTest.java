package com.alveesdev.xmlcollector.infra.xml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlFileScannerReadableTest {

	private final XmlFileScanner scanner = new XmlFileScanner();

	@TempDir
	Path root;

	@Test
	void acceptsAFolderThatCanBeRead() {
		assertDoesNotThrow(() -> scanner.requireReadable(root));
	}

	@Test
	void refusesAFolderThatDoesNotExist() {
		assertThrows(IOException.class, () -> scanner.requireReadable(root.resolve("nao-existe")));
	}
}
