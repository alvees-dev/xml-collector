package com.alveesdev.xmlcollector.infra.archive;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class XmlArchiveFindDocumentTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
	private static final String KEY = key(1, 10).value();

	private final XmlArchive archive = new XmlArchive((content, models) -> switch (new String(content, StandardCharsets.UTF_8)) {
		case "wanted" -> invoice(1, 10, DAY);
		case "other" -> invoice(1, 11, DAY);
		case "event" -> cancellation(10, DAY);
		default -> new XmlDocument.Unreadable("invalido");
	});

	@TempDir
	Path month;

	@Test
	void findsTheFileNamedWithTheKeyInASubfolder() throws IOException {
		Path file = create(month.resolve("NFCe").resolve("Dia_15").resolve(KEY + "-nfe.xml"), "wanted");

		assertEquals(Optional.of(file), archive.findDocument(month, KEY, "qualquer.xml"));
	}

	@Test
	void findsTheFileThatKeptItsOriginalName() throws IOException {
		Path file = create(month.resolve("NFCe").resolve("nota.xml"), "wanted");

		assertEquals(Optional.of(file), archive.findDocument(month, KEY, "nota.xml"));
	}

	@Test
	void skipsAnotherNoteWithTheSameNameInAnotherFolder() throws IOException {
		create(month.resolve("PDV1").resolve("nota.xml"), "other");
		Path file = create(month.resolve("PDV2").resolve("nota.xml"), "wanted");

		assertEquals(Optional.of(file), archive.findDocument(month, KEY, "nota.xml"));
	}

	@Test
	void doesNotTakeTheCancellationThatHasTheKeyInItsName() throws IOException {
		create(month.resolve("NFCe").resolve("110111" + KEY + "01.xml"), "event");

		assertTrue(archive.findDocument(month, KEY, "nota.xml").isEmpty());
	}

	@Test
	void findsNothingInAMonthThatDoesNotExist() throws IOException {
		assertTrue(archive.findDocument(month.resolve("ausente"), KEY, "nota.xml").isEmpty());
	}

	private static Path create(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		return Files.writeString(file, content);
	}
}
