package com.alveesdev.xmlcollector.infra.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;

class XmlArchiveVoidingLookupTest {

	private static final String NAME = "1-procInutNFe.xml";

	private final XmlArchive archive = new XmlArchive(new FiscalXmlReader());

	@TempDir
	Path root;

	@Test
	void findsAVoidingInAnotherMonth() throws IOException {
		create(october().resolve("Inutilizadas").resolve(NAME));

		assertTrue(archive.holdsVoidingInAnotherMonth(root, november(), NAME));
	}

	@Test
	void doesNotCountTheMonthBeingCollected() throws IOException {
		create(november().resolve("Inutilizadas").resolve(NAME));

		assertFalse(archive.holdsVoidingInAnotherMonth(root, november(), NAME));
	}

	@Test
	void doesNotCountAFileOutsideTheVoidingFolder() throws IOException {
		create(october().resolve("NFCe").resolve(NAME));

		assertFalse(archive.holdsVoidingInAnotherMonth(root, november(), NAME));
	}

	@Test
	void findsNothingInAnArchiveThatDoesNotExistYet() throws IOException {
		assertFalse(archive.holdsVoidingInAnotherMonth(root.resolve("vazio"), november(), NAME));
	}

	private Path october() {
		return root.resolve("2026").resolve("Outubro");
	}

	private Path november() {
		return root.resolve("2026").resolve("Novembro");
	}

	private static void create(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "<procInutNFe/>");
	}
}
