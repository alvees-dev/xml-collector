package com.alveesdev.xmlcollector.infra.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor.ExtraFile;

class MonthZipArchiverTest {

	private static final String ZIP_NAME = "ArquivoXML_12345678000190_Janeiro_2026";

	@TempDir
	Path root;

	private Path month;
	private final MonthZipArchiver archiver = new MonthZipArchiver();

	@BeforeEach
	void setUp() throws IOException {
		month = root.resolve("2026").resolve("Janeiro");
		Files.createDirectories(month.resolve("NFCe"));
	}

	@Test
	void namesTheZipAsRequestedAndLeavesNoTemporaryFile() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");

		CompressedFile zip = archiver.compress(month, ZIP_NAME, List.of()).orElseThrow();

		assertEquals(root.resolve("2026").resolve(ZIP_NAME + ".zip"), zip.file());
		assertEquals(Files.size(zip.file()), zip.bytes());
		assertEquals(List.of(ZIP_NAME + ".zip", "Janeiro"), namesIn(root.resolve("2026")));
	}

	@Test
	void keepsTheMonthFolderInsideTheZip() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");

		CompressedFile zip = archiver.compress(month, ZIP_NAME, List.of()).orElseThrow();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals("Janeiro_2026/NFCe/nota.xml", file.entries().nextElement().getName());
		}
	}

	@Test
	void sendingAgainReplacesTheSameFile() throws IOException {
		Path note = month.resolve("NFCe").resolve("nota.xml");
		Files.writeString(note, "<a/>");
		archiver.compress(month, ZIP_NAME, List.of());

		Files.writeString(month.resolve("NFCe").resolve("outra.xml"), "<b/>");
		CompressedFile zip = archiver.compress(month, ZIP_NAME, List.of()).orElseThrow();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals(2, file.size());
		}
	}

	@Test
	void createsNothingForAnEmptyFolder() throws IOException {
		Optional<CompressedFile> zip = archiver.compress(month, ZIP_NAME, List.of());

		assertTrue(zip.isEmpty());
		assertFalse(Files.exists(root.resolve("2026").resolve(ZIP_NAME + ".zip")));
	}

	@Test
	void putsTheExtraFileInsideTheMonthFolderOfTheZipOnly() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");

		CompressedFile zip = archiver.compress(month, ZIP_NAME, List.of(new ExtraFile("Relatório.txt", new byte[] {1, 2}))).orElseThrow();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals(2, file.size());
			assertArrayEquals(new byte[] {1, 2}, file.getInputStream(file.getEntry("Janeiro_2026/Relatório.txt")).readAllBytes());
		}

		assertEquals(List.of("NFCe"), namesIn(month));
	}

	@Test
	void anExtraFileDoesNotMakeAnEmptyFolderWorthAZip() throws IOException {
		Optional<CompressedFile> zip = archiver.compress(month, ZIP_NAME, List.of(new ExtraFile("Relatório.txt", new byte[] {1})));

		assertTrue(zip.isEmpty());
		assertFalse(Files.exists(root.resolve("2026").resolve(ZIP_NAME + ".zip")));
	}

	@Test
	void findsTheZipOfTheMonthWhateverTheCnpjsInItsName() throws IOException {
		Path zip = Files.writeString(root.resolve("2026").resolve(ZIP_NAME + ".zip"), "zip");

		CompressedFile existing = archiver.existing(month).orElseThrow();

		assertEquals(zip, existing.file());
		assertEquals(3, existing.bytes());
	}

	@Test
	void findsTheZipOfAMonthWithoutACnpj() throws IOException {
		Path zip = Files.writeString(root.resolve("2026").resolve("ArquivoXML_Janeiro_2026.zip"), "zip");

		assertEquals(zip, archiver.existing(month).orElseThrow().file());
	}

	@Test
	void ignoresTheZipsOfOtherMonthsAndUnfinishedOnes() throws IOException {
		Files.writeString(root.resolve("2026").resolve("ArquivoXML_12345678000190_Fevereiro_2026.zip"), "zip");
		Files.writeString(root.resolve("2026").resolve(ZIP_NAME + ".zip.part"), "zip");
		Files.writeString(root.resolve("2026").resolve("Janeiro_2026.zip"), "zip");

		assertTrue(archiver.existing(month).isEmpty());
	}

	@Test
	void stopsWhenCancelledAndKeepsThePreviousZip() throws IOException {
		Files.writeString(month.resolve("NFCe").resolve("nota.xml"), "<a/>");
		archiver.compress(month, ZIP_NAME, List.of()).orElseThrow();
		long previousSize = Files.size(root.resolve("2026").resolve(ZIP_NAME + ".zip"));

		Files.writeString(month.resolve("NFCe").resolve("outra.xml"), "<b/>");
		Thread.currentThread().interrupt();

		try {
			assertThrows(IOException.class, () -> archiver.compress(month, ZIP_NAME, List.of()));
		} finally {
			Thread.interrupted();
		}

		assertEquals(previousSize, Files.size(root.resolve("2026").resolve(ZIP_NAME + ".zip")));
		assertEquals(List.of(ZIP_NAME + ".zip", "Janeiro"), namesIn(root.resolve("2026")));
	}

	@Test
	void compressingAgainRightAfterCancellingWorks() throws Exception {
		int files = 3000;

		for (int index = 0; index < files; index++) {
			Files.writeString(month.resolve("NFCe").resolve(index + ".xml"), "<nota>" + index + "</nota>");
		}

		Thread cancelled = new Thread(() -> {
			try {
				archiver.compress(month, ZIP_NAME, List.of());
			} catch (IOException e) {
				// What is expected from a compression that was cancelled.
			}
		});

		cancelled.start();
		cancelled.interrupt();

		CompressedFile zip = archiver.compress(month, ZIP_NAME, List.of()).orElseThrow();
		cancelled.join();

		try (ZipFile file = new ZipFile(zip.file().toFile())) {
			assertEquals(files, file.size());
		}

		assertEquals(List.of(ZIP_NAME + ".zip", "Janeiro"), namesIn(root.resolve("2026")));
	}

	private static List<String> namesIn(Path directory) throws IOException {
		List<String> names = new ArrayList<>();

		try (var files = Files.list(directory)) {
			files.map(file -> file.getFileName().toString()).sorted().forEach(names::add);
		}

		return names;
	}
}
