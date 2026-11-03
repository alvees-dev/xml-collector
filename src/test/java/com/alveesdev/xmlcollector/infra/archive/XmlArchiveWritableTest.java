package com.alveesdev.xmlcollector.infra.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;

class XmlArchiveWritableTest {

	private final XmlArchive archive = new XmlArchive(new FiscalXmlReader());

	@TempDir
	Path root;

	@Test
	void acceptsAFolderThatCanBeWrittenAndLeavesNothingBehind() throws IOException {
		archive.requireWritable(root);

		try (var files = Files.list(root)) {
			assertEquals(List.of(), files.toList());
		}
	}

	@Test
	void acceptsAFolderToBeCreatedInsideAWritableOneWithoutCreatingIt() throws IOException {
		Path folder = root.resolve("xmls").resolve("inutilizadas");

		archive.requireWritable(folder);

		assertFalse(Files.exists(root.resolve("xmls")));
	}

	@Test
	void refusesAFolderOfAnUnavailableRoot() {
		assertThrows(IOException.class, () -> archive.requireWritable(Path.of("relativa-que-nao-existe", "saida")));
	}
}
