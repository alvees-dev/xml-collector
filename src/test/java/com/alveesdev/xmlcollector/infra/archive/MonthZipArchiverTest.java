package com.alveesdev.xmlcollector.infra.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor.CompressedFile;

class MonthZipArchiverTest {

	private static final String ZIP_NAME = "ArquivoXML_12345678000190_Janeiro_2026";

	@TempDir
	Path root;

	private Path month;
	private final MonthZipArchiver archiver = new MonthZipArchiver();

	@BeforeEach
	void setUp() throws IOException {
		month = root.resolve("Janeiro_2026");
		Files.createDirectories(month.resolve("NFCe"));
	}

	@Test
	void namesTheZipAsRequestedAndLeavesNoTemporaryFile() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");

		CompressedFile zip = archiver.compress(month, ZIP_NAME).orElseThrow();

		assertEquals(root.resolve(ZIP_NAME + ".zip"), zip.file());
		assertEquals(Files.size(zip.file()), zip.bytes());
		assertEquals(List.of(ZIP_NAME + ".zip", "Janeiro_2026"), namesIn(root));
	}

	@Test
	void keepsTheMonthFolderInsideTheZip() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");

		CompressedFile zip = archiver.compress(month, ZIP_NAME).orElseThrow();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals("Janeiro_2026/NFCe/nota.xml", file.entries().nextElement().getName());
		}
	}

	@Test
	void sendingAgainReplacesTheSameFile() throws IOException {
		Path note = month.resolve("NFCe").resolve("nota.xml");
		Files.writeString(note, "<a/>");
		archiver.compress(month, ZIP_NAME);

		Files.writeString(month.resolve("NFCe").resolve("outra.xml"), "<b/>");
		CompressedFile zip = archiver.compress(month, ZIP_NAME).orElseThrow();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals(2, file.size());
		}
	}

	@Test
	void createsNothingForAnEmptyFolder() throws IOException {
		Optional<CompressedFile> zip = archiver.compress(month, ZIP_NAME);

		assertTrue(zip.isEmpty());
		assertFalse(Files.exists(root.resolve(ZIP_NAME + ".zip")));
	}

	private static List<String> namesIn(Path directory) throws IOException {
		List<String> names = new ArrayList<>();

		try (var files = Files.list(directory)) {
			files.map(file -> file.getFileName().toString()).sorted().forEach(names::add);
		}

		return names;
	}
}
