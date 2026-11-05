package com.alveesdev.xmlcollector.ui.model;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
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
}
