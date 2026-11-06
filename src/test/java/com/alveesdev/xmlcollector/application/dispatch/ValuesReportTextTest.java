package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.ReportPdfWriter;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

class ValuesReportTextTest {

	private static final String MONTH = "Outubro de 2026";
	private static final DispatchMonth OCTOBER = DispatchMonth.collected(Path.of("arquivos"), YearMonth.of(2026, 10));
	private static final ValuesReport NO_DOCUMENTS = new ValuesReport(List.of(), 0);
	private static final ValuesReport.Section NFCE = new ValuesReport.Section("12345678000190", "65", 1, 100, 250,
			List.of(120, 130), List.of(new NumberRange(140, 142), new NumberRange(150, 150)), 142, new BigDecimal("12345.60"));
	private static final ValuesReport.Section NFE_SALES = new ValuesReport.Section("12345678000190", "55", 1, 7, 9,
			List.of(8), List.of(), 2, new BigDecimal("1000.00"));
	private static final ValuesReport.Section NFE_PURCHASE = new ValuesReport.Section("98765432000110", "55", 1, 50, 50,
			List.of(), List.of(), 1, new BigDecimal("250.50"));
	private static final ValuesReport COMPANY = new ValuesReport(List.of(NFCE, NFE_SALES), 0);
	private static final ValuesReport PURCHASES = new ValuesReport(List.of(NFE_PURCHASE), 0);

	@Test
	void theNfceReportHasEveryPartOfTheNfceAndItsOwnTotal() {
		String text = ValuesReportText.nfceTextOf(MONTH, COMPANY, NO_DOCUMENTS);

		assertEquals(String.join("\r\n",
				"RELATÓRIO NFCe - Outubro de 2026",
				"",
				"CNPJ 12345678000190 - NFC-e - Série 1",
				"Primeira nota emitida: 100",
				"Última nota emitida: 250",
				"Notas canceladas: 2 notas",
				"Notas inutilizadas: 4 notas",
				"Total de vendas: 142 notas",
				"Valor total das vendas: R$ 12.345,60",
				"",
				"TOTAL DO MÊS",
				"Total de vendas: 142 notas",
				"Valor total das vendas: R$ 12.345,60",
				""), text);
	}

	@Test
	void theNfeReportHasTheSalesAndThePurchasesApart() {
		String text = ValuesReportText.nfeTextOf(MONTH, COMPANY, PURCHASES, new ReportOptions(false, true, true));

		assertEquals(String.join("\r\n",
				"RELATÓRIO NFe - Outubro de 2026",
				"",
				"NF-e - VENDAS",
				"Total de vendas: 2 notas",
				"Valor total das vendas: R$ 1.000,00",
				"",
				"NF-e - COMPRAS",
				"Valor total das compras: R$ 250,50",
				""), text);
	}

	@Test
	void eachReportHasOnlyItsOwnKind() {
		String nfce = ValuesReportText.nfceTextOf(MONTH, COMPANY, PURCHASES);
		String onlyPurchases = ValuesReportText.nfeTextOf(MONTH, COMPANY, PURCHASES, new ReportOptions(false, false, true));

		assertFalse(nfce.contains("NF-e"), nfce);
		assertFalse(nfce.contains("R$ 1.000,00"), nfce);
		assertFalse(onlyPurchases.contains("VENDAS"), onlyPurchases);
		assertFalse(onlyPurchases.contains("NFC-e"), onlyPurchases);
		assertTrue(onlyPurchases.contains("Valor total das compras: R$ 250,50"), onlyPurchases);
	}

	@Test
	void theFilesAreNamedAfterTheMonth() {
		List<DirectoryCompressor.ExtraFile> files = ValuesReportText.filesOf(OCTOBER, COMPANY, PURCHASES, new ReportOptions(true, true, true));

		assertEquals(List.of("Relatório NFCe Outubro 2026.txt", "Relatório NFe Outubro 2026.txt"), files.stream().map(DirectoryCompressor.ExtraFile::name).toList());
	}

	@Test
	void onlyTheCheckedReportsAreMade() {
		assertEquals(List.of("Relatório NFCe Outubro 2026.txt"),
				ValuesReportText.filesOf(OCTOBER, COMPANY, PURCHASES, ReportOptions.STANDARD).stream().map(DirectoryCompressor.ExtraFile::name).toList());
		assertEquals(List.of("Relatório NFe Outubro 2026.txt"),
				ValuesReportText.filesOf(OCTOBER, COMPANY, PURCHASES, new ReportOptions(false, false, true)).stream().map(DirectoryCompressor.ExtraFile::name).toList());
		assertEquals(List.of(), ValuesReportText.filesOf(OCTOBER, COMPANY, PURCHASES, new ReportOptions(false, false, false)));
	}

	@Test
	void aCheckedKindWithoutNotesShowsZero() {
		String text = ValuesReportText.nfeTextOf(MONTH, NO_DOCUMENTS, NO_DOCUMENTS, new ReportOptions(false, true, true));

		assertTrue(text.contains("Valor total das vendas: R$ 0,00"), text);
		assertTrue(text.contains("Valor total das compras: R$ 0,00"), text);
	}

	@Test
	void anNfceReportWithoutNotesSaysSo() {
		String text = ValuesReportText.nfceTextOf(MONTH, NO_DOCUMENTS, NO_DOCUMENTS);

		assertTrue(text.contains("Nenhuma nota encontrada."), text);
		assertFalse(text.contains("TOTAL DO MÊS"), text);
	}

	@Test
	void showsWhatASeriesDoesNotHave() {
		ValuesReport.Section onlyVoided = new ValuesReport.Section("12345678000190", "65", 2, null, null, List.of(),
				List.of(), 1, BigDecimal.ZERO);

		String text = ValuesReportText.nfceTextOf(MONTH, new ValuesReport(List.of(onlyVoided), 0), NO_DOCUMENTS);

		assertTrue(text.contains("CNPJ 12345678000190 - NFC-e - Série 2"), text);
		assertTrue(text.contains("Primeira nota emitida: -"), text);
		assertTrue(text.contains("Notas canceladas: Nenhuma"), text);
		assertTrue(text.contains("Notas inutilizadas: Nenhuma"), text);
		assertTrue(text.contains("Total de vendas: 1 nota\r\n"), text);
	}

	@Test
	void bothReportsWarnAboutFilesThatCouldNotBeRead() {
		ValuesReport broken = new ValuesReport(List.of(), 2);
		ValuesReport brokenPurchase = new ValuesReport(List.of(), 1);

		assertTrue(ValuesReportText.nfceTextOf(MONTH, broken, brokenPurchase).contains("ATENÇÃO: 3 arquivo(s) não puderam ser lidos"));
		assertTrue(ValuesReportText.nfeTextOf(MONTH, broken, brokenPurchase, new ReportOptions(false, true, true)).contains("ATENÇÃO: 3 arquivo(s) não puderam ser lidos"));
	}

	@Test
	void theBytesStartWithTheMarkOfTheEncoding() {
		String decoded = new String(ValuesReportText.filesOf(OCTOBER, NO_DOCUMENTS, NO_DOCUMENTS, ReportOptions.STANDARD).get(0).content(), StandardCharsets.UTF_8);

		assertTrue(decoded.startsWith(String.valueOf((char) 0xFEFF) + "RELATÓRIO"), decoded);
	}

	@Test
	void thePdfHasTheCheckedReportsOneAfterTheOther() {
		List<ReportPdfWriter.Line> lines = ValuesReportText.pdfLinesOf(OCTOBER, COMPANY, PURCHASES, new ReportOptions(true, false, true));
		List<String> texts = lines.stream().map(ReportPdfWriter.Line::text).toList();

		assertEquals("RELATÓRIO NFCe - Outubro de 2026", texts.get(0));
		assertTrue(texts.contains("RELATÓRIO NFe - Outubro de 2026"));
		assertTrue(texts.contains("NF-e - COMPRAS"));
		assertFalse(texts.contains("NF-e - VENDAS"));
		assertTrue(lines.get(0).heading());
		assertFalse(lines.stream().filter(line -> line.text().startsWith("Total de vendas")).findFirst().orElseThrow().heading());
	}

	@Test
	void thePdfIsNamedAfterTheMonth() {
		assertEquals("Relatório Outubro 2026.pdf", ValuesReportText.pdfFileName(OCTOBER));
	}
}
