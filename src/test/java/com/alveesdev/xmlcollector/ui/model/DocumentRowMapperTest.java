package com.alveesdev.xmlcollector.ui.model;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class DocumentRowMapperTest {

	private static final XmlDocument.Invoice INVOICE = invoice(2, 15, LocalDate.of(2026, 1, 20));

	@Test
	void showsTheDataOfAnInvoice() {
		DocumentEntry entry = DocumentEntry.of(INVOICE, false, "nota.xml");

		DocumentRow row = DocumentRowMapper.toRow(entry);

		assertSame(entry, row.getEntry());
		assertEquals("15", row.getNumber());
		assertEquals("2", row.getSeries());
		assertEquals("R$\u00A010,50", row.getTotalValue());
		assertEquals("20/01/26    12:00", row.getEmissionDate());
		assertEquals(INVOICE.key().value(), row.getAccessKey());
		assertEquals("Autorizada", row.getStatus());
	}

	@Test
	void marksACancelledInvoice() {
		assertEquals("Cancelada", DocumentRowMapper.toRow(DocumentEntry.of(INVOICE, true, "nota.xml")).getStatus());
	}

	@Test
	void aFileThatCouldNotBeReadShowsOnlyItsNameAndTheError() {
		DocumentRow row = DocumentRowMapper.toRow(DocumentEntry.error("quebrado.xml"));

		assertEquals("", row.getNumber());
		assertEquals("", row.getSeries());
		assertEquals("", row.getTotalValue());
		assertEquals("", row.getEmissionDate());
		assertEquals("quebrado.xml", row.getAccessKey());
		assertEquals("Erro na Leitura", row.getStatus());
	}

	@Test
	void formatsThousandsInTheBrazilianWay() {
		assertEquals("R$\u00A01.234,50", DocumentRowMapper.currency(new BigDecimal("1234.50")));
	}

	@Test
	void aVoidedRangeGivesOneRowForEachNumberWithOnlyTheNumberKnown() {
		List<DocumentRow> rows = DocumentRowMapper.voidedRows(new TreeMap<>(Map.of(1, List.of(new NumberRange(5, 6)))));

		assertEquals(List.of("5", "6"), rows.stream().map(DocumentRow::getNumber).toList());
		DocumentRow row = rows.get(0);
		assertEquals("1", row.getSeries());
		assertEquals("-", row.getTotalValue());
		assertEquals("-", row.getEmissionDate());
		assertEquals("-", row.getAccessKey());
		assertEquals("Inutilizada", row.getStatus());
		assertEquals(DocumentStatus.VOIDED, row.getEntry().status());
	}

	@Test
	void theRowsGoBySeriesAndNumberWithTheUnreadableFilesLast() {
		DocumentRow unreadable = DocumentRowMapper.toRow(DocumentEntry.error("quebrado.xml"));
		List<DocumentRow> voided = DocumentRowMapper.voidedRows(new TreeMap<>(Map.of(1, List.of(new NumberRange(2, 2)), 2, List.of(new NumberRange(1, 1)))));
		DocumentRow first = DocumentRowMapper.toRow(DocumentEntry.of(invoice(1, LocalDate.of(2026, 10, 1)), false, "1.xml"));
		DocumentRow tenth = DocumentRowMapper.toRow(DocumentEntry.of(invoice(10, LocalDate.of(2026, 10, 1)), false, "10.xml"));

		List<DocumentRow> sorted = java.util.stream.Stream.of(unreadable, tenth, voided.get(1), first, voided.get(0))
				.sorted(DocumentRowMapper.BY_SERIES_AND_NUMBER).toList();

		assertEquals(List.of(first, voided.get(0), tenth, voided.get(1), unreadable), sorted);
	}
}
