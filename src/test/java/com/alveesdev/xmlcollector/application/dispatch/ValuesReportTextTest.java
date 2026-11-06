package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

class ValuesReportTextTest {

	private static final String MONTH = "Outubro de 2026";
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
	void writesEveryPartOfTheNfce() {
		String text = ValuesReportText.textOf(MONTH, COMPANY, NO_DOCUMENTS, new ReportOptions(true, false, false));

		assertEquals(String.join("\r\n",
				"RELATÓRIO DE VALORES - Outubro de 2026",
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
	void theNfeShowsOnlyTheValue() {
		String text = ValuesReportText.textOf(MONTH, COMPANY, PURCHASES, new ReportOptions(false, true, true));

		assertEquals(String.join("\r\n",
				"RELATÓRIO DE VALORES - Outubro de 2026",
				"",
				"NF-e - VENDAS",
				"Valor total das vendas: R$ 1.000,00",
				"",
				"NF-e - COMPRAS",
				"Valor total das compras: R$ 250,50",
				"",
				"TOTAL DO MÊS",
				"Total de vendas: 2 notas",
				"Valor total das vendas: R$ 1.000,00",
				""), text);
	}

	@Test
	void theTotalOfTheMonthJoinsTheNfceAndTheSalesButNotThePurchases() {
		String text = ValuesReportText.textOf(MONTH, COMPANY, PURCHASES, new ReportOptions(true, true, true));

		assertTrue(text.endsWith(String.join("\r\n",
				"TOTAL DO MÊS",
				"Total de vendas: 144 notas",
				"Valor total das vendas: R$ 13.345,60",
				"")), text);
	}

	@Test
	void anUncheckedKindDoesNotAppear() {
		String text = ValuesReportText.textOf(MONTH, COMPANY, PURCHASES, new ReportOptions(true, false, false));

		assertFalse(text.contains("NF-e"), text);
		assertFalse(text.contains("R$ 1.000,00"), text);
		assertFalse(text.contains("250,50"), text);
	}

	@Test
	void onlyThePurchasesHaveNoTotalOfSales() {
		String text = ValuesReportText.textOf(MONTH, NO_DOCUMENTS, PURCHASES, new ReportOptions(false, false, true));

		assertTrue(text.contains("Valor total das compras: R$ 250,50"), text);
		assertFalse(text.contains("TOTAL DO MÊS"), text);
	}

	@Test
	void aCheckedKindWithoutNotesShowsZero() {
		String text = ValuesReportText.textOf(MONTH, NO_DOCUMENTS, NO_DOCUMENTS, new ReportOptions(false, true, true));

		assertTrue(text.contains("Valor total das vendas: R$ 0,00"), text);
		assertTrue(text.contains("Valor total das compras: R$ 0,00"), text);
		assertFalse(text.contains("Nenhuma nota encontrada."), text);
	}

	@Test
	void showsWhatASeriesDoesNotHave() {
		ValuesReport.Section onlyVoided = new ValuesReport.Section("12345678000190", "65", 2, null, null, List.of(),
				List.of(), 1, BigDecimal.ZERO);

		String text = ValuesReportText.textOf(MONTH, new ValuesReport(List.of(onlyVoided), 0), NO_DOCUMENTS, ReportOptions.STANDARD);

		assertTrue(text.contains("CNPJ 12345678000190 - NFC-e - Série 2"), text);
		assertTrue(text.contains("Primeira nota emitida: -"), text);
		assertTrue(text.contains("Notas canceladas: Nenhuma"), text);
		assertTrue(text.contains("Notas inutilizadas: Nenhuma"), text);
		assertTrue(text.contains("Total de vendas: 1 nota\r\n"), text);
	}

	@Test
	void warnsAboutFilesThatCouldNotBeRead() {
		String text = ValuesReportText.textOf(MONTH, new ValuesReport(List.of(), 2), new ValuesReport(List.of(), 1), ReportOptions.STANDARD);

		assertTrue(text.contains("ATENÇÃO: 3 arquivo(s) não puderam ser lidos"), text);
		assertTrue(text.contains("Nenhuma nota encontrada."), text);
	}

	@Test
	void theBytesStartWithTheMarkOfTheEncoding() {
		String decoded = new String(ValuesReportText.bytesOf(MONTH, NO_DOCUMENTS, NO_DOCUMENTS, ReportOptions.STANDARD), StandardCharsets.UTF_8);

		assertTrue(decoded.startsWith(String.valueOf((char) 0xFEFF) + "RELATÓRIO"), decoded);
	}
}
