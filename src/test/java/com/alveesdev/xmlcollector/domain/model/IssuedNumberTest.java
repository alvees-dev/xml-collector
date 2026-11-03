package com.alveesdev.xmlcollector.domain.model;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class IssuedNumberTest {

	@Test
	void takesCnpjAndModelFromTheAccessKey() {
		XmlDocument.Invoice invoice = invoice(2, 7, LocalDate.of(2026, 1, 15));

		assertEquals(new IssuedNumber("12345678000190", "65", 2, 7), IssuedNumber.of(invoice));
	}
}
