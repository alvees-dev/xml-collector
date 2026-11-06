package com.alveesdev.xmlcollector.application.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

class CollectionRequestTest {

	@TempDir
	Path root;

	@Test
	void findsADirectoryChosenInsideTheArchive() {
		Path archive = root.resolve("Arquivos_XML");
		String month = archive.resolve("2026").resolve("Outubro_2026").toString();

		assertEquals(Optional.of(month), requestFor(archive, "", root.resolve("pdv").toString(), month).directoryInsideTheArchive());
	}

	@Test
	void theArchiveItselfAlsoCounts() {
		Path archive = root.resolve("Arquivos_XML");

		assertEquals(Optional.of(archive.toString()), requestFor(archive, archive.toString()).directoryInsideTheArchive());
	}

	@Test
	void aDirectoryAboveTheArchiveCanBeCollected() {
		Path archive = root.resolve("Arquivos_XML");

		assertEquals(Optional.empty(), requestFor(archive, root.toString(), root.resolve("Arquivos_XML_antigo").toString()).directoryInsideTheArchive());
	}

	private static CollectionRequest requestFor(Path archive, String... directories) {
		return new CollectionRequest(List.of(directories), DocumentCategory.NFCE,
				new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), OrganizeMode.BY_DAY, archive,
				List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);
	}
}
