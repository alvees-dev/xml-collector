package com.alveesdev.xmlcollector.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

class ArchiveLayoutTest {

	private static final Path ROOT = Path.of("arquivos");
	private static final LocalDate DAY = LocalDate.of(2026, 3, 5);
	private static final Path MARCH = ROOT.resolve("2026").resolve("Março_2026");
	private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

	@Test
	void namesEveryMonthInOrder() {
		List<String> names = ArchiveLayout.monthNames();

		assertEquals(12, names.size());
		assertEquals("Janeiro", names.get(0));
		assertEquals("Dezembro", names.get(11));
	}

	@Test
	void theMonthOfADateIsAFolderInsideItsYear() {
		assertEquals(MARCH, ArchiveLayout.monthDirectory(ROOT, DAY));
		assertEquals("Março_2026", ArchiveLayout.monthYearName(DAY));
	}

	@Test
	void theZipStaysInTheFolderOfTheYearWithTheMarkOfItsSource() {
		assertEquals(ROOT.resolve("2026"), ArchiveLayout.zipFolder(ROOT, MARCH_2026));
		assertEquals("Março_2026p", ArchiveLayout.zipLabel(MARCH_2026, "p"));
		assertEquals("Março_2026s", ArchiveLayout.zipLabel(MARCH_2026, "s"));
	}

	@Test
	void aPeriodCoversEveryMonthAcrossTheYear() {
		DateRange period = new DateRange(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 2, 1));

		assertEquals(List.of(
				ROOT.resolve("2025").resolve("Dezembro_2025"),
				ROOT.resolve("2026").resolve("Janeiro_2026"),
				ROOT.resolve("2026").resolve("Fevereiro_2026")), ArchiveLayout.monthDirectories(ROOT, period));
	}

	@Test
	void theZipNameHasEachCnpjOnceInOrder() {
		assertEquals("ArquivoXML_111_222_Março_2026p", ArchiveLayout.zipName(List.of("222", "111", "222"), "Março_2026p"));
	}

	@Test
	void recognizesTheZipOfTheMonthWhateverItsCnpjs() {
		assertTrue(ArchiveLayout.isZipOf("ArquivoXML_111_Março_2026p.zip", "Março_2026p"));
		assertTrue(ArchiveLayout.isZipOf("ArquivoXML_111_222_Março_2026p.zip", "Março_2026p"));
	}

	@Test
	void doesNotTakeAnotherFileAsTheZipOfTheMonth() {
		assertFalse(ArchiveLayout.isZipOf("ArquivoXML_111_Abril_2026p.zip", "Março_2026p"));
		assertFalse(ArchiveLayout.isZipOf("Outro_111_Março_2026p.zip", "Março_2026p"));
		assertFalse(ArchiveLayout.isZipOf("ArquivoXML_111_Março_2026p.zip.part", "Março_2026p"));
	}

	@Test
	void theZipOfTheServerIsNotTheZipOfTheCollection() {
		assertFalse(ArchiveLayout.isZipOf("ArquivoXML_111_Março_2026s.zip", "Março_2026p"));
		assertFalse(ArchiveLayout.isZipOf("ArquivoXML_111_Março_2026p.zip", "Março_2026s"));
	}

	@Test
	void theVoidingFileIsNamedAfterItsIdWithoutThePrefix() {
		assertEquals("3526123-procInutNFe.xml", ArchiveLayout.voidingFileName("ID3526123"));
	}

	@Test
	void aDocumentGoesToTheFolderOfItsCategory() {
		assertEquals(MARCH.resolve("NFCe"), ArchiveLayout.directoryFor(ROOT, DAY, OrganizeMode.SINGLE_FOLDER, DocumentCategory.NFCE));
	}

	@Test
	void organizingByDayAddsTheFolderOfTheDay() {
		assertEquals(MARCH.resolve("NFe").resolve("Vendas").resolve("Dia_05"),
				ArchiveLayout.directoryFor(ROOT, DAY, OrganizeMode.BY_DAY, DocumentCategory.NFE_SALES));
	}

	@Test
	void aVoidingHasItsOwnFolderInTheMonth() {
		assertEquals(MARCH.resolve("Inutilizadas"), ArchiveLayout.voidingDirectoryFor(ROOT, DAY));
	}

	@Test
	void theFolderOfAPdvCountsFromOne() {
		assertEquals(MARCH.resolve("PDV1"), ArchiveLayout.pdvDirectoryFor(ROOT, DAY, OrganizeMode.SINGLE_FOLDER, 0));
		assertEquals(MARCH.resolve("PDV3").resolve("Dia_05"), ArchiveLayout.pdvDirectoryFor(ROOT, DAY, OrganizeMode.BY_DAY, 2));
	}
}
