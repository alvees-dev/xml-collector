package com.alveesdev.xmlcollector.domain.model;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class IssuedNumberTest {

	@Test
	void takesCnpjAndModelFromTheAccessKeyAndKeepsTheDate() {
		LocalDate date = LocalDate.of(2026, 1, 15);

		assertEquals(new IssuedNumber("12345678000190", "65", 2, 7, date), IssuedNumber.of(invoice(2, 7, date)));
	}
}
