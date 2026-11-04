package com.alveesdev.xmlcollector.domain.service;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class ValuesReportTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
	private static final String CNPJ = "12345678000190";

	@Test
	void givesTheFirstAndLastNumbersWhateverTheOrder() {
		ValuesReport.Section section = onlySection(List.of(invoice(9, DAY), invoice(3, DAY), invoice(5, DAY)));

		assertEquals(3, section.firstNumber());
		assertEquals(9, section.lastNumber());
		assertEquals(CNPJ, section.cnpj());
		assertEquals("65", section.modelCode());
	}

	@Test
	void aCancelledInvoiceCountsAsEmittedButNotAsASale() {
		ValuesReport.Section section = onlySection(List.of(invoice(1, DAY), invoice(2, DAY), cancellation(2, DAY)));

		assertEquals(2, section.lastNumber());
		assertEquals(List.of(2), section.cancelledNumbers());
		assertEquals(1, section.sales());
		assertEquals(new BigDecimal("10.50"), section.salesValue());
	}

	@Test
	void theCancellationCanComeBeforeItsInvoice() {
		ValuesReport.Section section = onlySection(List.of(cancellation(1, DAY), invoice(1, DAY)));

		assertEquals(List.of(1), section.cancelledNumbers());
		assertEquals(0, section.sales());
	}

	@Test
	void aCancellationOfAnotherMonthDoesNotEnterTheList() {
		ValuesReport.Section section = onlySection(List.of(invoice(1, DAY), cancellation(99, DAY)));

		assertEquals(List.of(), section.cancelledNumbers());
		assertEquals(1, section.sales());
	}

	@Test
	void countsARepeatedInvoiceOnce() {
		ValuesReport.Section section = onlySection(List.of(invoice(1, DAY), invoice(1, DAY)));

		assertEquals(1, section.sales());
	}

	@Test
	void listsTheVoidedRangesInOrderAndOnlyOnce() {
		ValuesReport.Section section = onlySection(List.of(invoice(1, DAY), voiding(1, 8, 9), voiding(1, 3, 3), voiding(1, 8, 9)));

		assertEquals(List.of(new NumberRange(3, 3), new NumberRange(8, 9)), section.voidedRanges());
	}

	@Test
	void aSeriesWithOnlyVoidedNumbersHasNoFirstOrLast() {
		ValuesReport.Section section = onlySection(List.of(voiding(2, 5, 5)));

		assertNull(section.firstNumber());
		assertNull(section.lastNumber());
		assertEquals(2, section.series());
		assertEquals(0, section.sales());
		assertEquals(BigDecimal.ZERO, section.salesValue());
	}

	@Test
	void separatesTheSeriesAndAddsThemInTheTotals() {
		ValuesReport report = ValuesReport.of(List.of(invoice(2, 7, DAY), invoice(1, 3, DAY), invoice(1, 4, DAY)));

		assertEquals(List.of(1, 2), report.sections().stream().map(ValuesReport.Section::series).toList());
		assertEquals(3, report.totalSales());
		assertEquals(new BigDecimal("31.50"), report.totalValue());
	}

	@Test
	void countsTheUnreadableFilesAndSkipsTheIgnoredOnes() {
		ValuesReport report = ValuesReport.of(List.of(new XmlDocument.Unreadable("ruim"), new XmlDocument.Ignored("evento")));

		assertEquals(1, report.unreadableFiles());
		assertEquals(List.of(), report.sections());
		assertEquals(BigDecimal.ZERO, report.totalValue());
	}

	private static ValuesReport.Section onlySection(List<XmlDocument> documents) {
		List<ValuesReport.Section> sections = ValuesReport.of(documents).sections();

		assertEquals(1, sections.size());
		return sections.get(0);
	}

	private static XmlDocument.Voiding voiding(int series, int start, int end) {
		String id = String.format("ID3526%s65%03d%09d%09d", CNPJ, series, start, end);

		return new XmlDocument.Voiding(id, series, new NumberRange(start, end), DAY.atTime(12, 0).atOffset(ZoneOffset.ofHours(-3)));
	}
}
