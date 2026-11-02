package com.alveesdev.xmlcollector.domain.service;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument.Invoice;

class MissingNumberFinderTest {

	private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

	@Test
	void noInvoicesMeansNoSeries() {
		assertTrue(MissingNumberFinder.findBySeries(List.of()).isEmpty());
	}

	@Test
	void aContinuousSequenceHasNoGaps() {
		assertEquals(Map.of(1, List.of()), MissingNumberFinder.findBySeries(invoices(1, 2, 3)));
	}

	@Test
	void findsSingleAndMultipleNumberGaps() {
		assertEquals(
				Map.of(1, List.of(new NumberRange(2, 2), new NumberRange(4, 6))),
				MissingNumberFinder.findBySeries(invoices(7, 1, 3)));
	}

	@Test
	void doesNotReportNumbersBeforeTheFirstOrAfterTheLast() {
		assertEquals(Map.of(1, List.of()), MissingNumberFinder.findBySeries(invoices(100, 101)));
	}

	@Test
	void repeatedNumbersDoNotCreateGaps() {
		assertEquals(Map.of(1, List.of()), MissingNumberFinder.findBySeries(invoices(1, 1, 2)));
	}

	@Test
	void separatesGapsBySeries() {
		List<Invoice> invoices = List.of(invoice(1, 1, DATE), invoice(1, 3, DATE), invoice(2, 10, DATE), invoice(2, 13, DATE));

		assertEquals(
				Map.of(1, List.of(new NumberRange(2, 2)), 2, List.of(new NumberRange(11, 12))),
				MissingNumberFinder.findBySeries(invoices));
	}

	private static List<Invoice> invoices(int... numbers) {
		return IntStream.of(numbers).mapToObj(number -> invoice(number, DATE)).toList();
	}
}
