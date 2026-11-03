package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoidingRequestTest {

	private static final String JUSTIFICATION = "Numeracao nao utilizada";

	@Test
	void buildsTheIdFromItsFields() {
		VoidingRequest request = request("35", 2026, 1, new NumberRange(12, 15), JUSTIFICATION);

		assertEquals("ID352612345678000190650010000000120000000" + "15", request.id());
		assertEquals(43, request.id().length());
	}

	@Test
	void stripsTheJustification() {
		assertEquals(JUSTIFICATION, request("35", 2026, 1, new NumberRange(1, 1), "  " + JUSTIFICATION + " ").justification());
	}

	@Test
	void rejectsAShortOrLongJustification() {
		assertInvalid("35", 2026, 1, new NumberRange(1, 1), "curta demais");
		assertInvalid("35", 2026, 1, new NumberRange(1, 1), "              x              ");
		assertInvalid("35", 2026, 1, new NumberRange(1, 1), null);
		assertInvalid("35", 2026, 1, new NumberRange(1, 1), "x".repeat(256));
	}

	@Test
	void rejectsInvalidFields() {
		assertInvalid("3", 2026, 1, new NumberRange(1, 1), JUSTIFICATION);
		assertInvalid("35", 1999, 1, new NumberRange(1, 1), JUSTIFICATION);
		assertInvalid("35", 2026, 1000, new NumberRange(1, 1), JUSTIFICATION);
		assertInvalid("35", 2026, 1, new NumberRange(0, 1), JUSTIFICATION);
		assertInvalid("35", 2026, 1, new NumberRange(1, 1_000_000_000), JUSTIFICATION);
	}

	@Test
	void onlyStatus102IsApproved() {
		assertTrue(new VoidingReceipt("102", "ok", "1", "agora", "<x/>").approved());
		assertFalse(new VoidingReceipt("241", "ja usado", null, "agora", "<x/>").approved());
	}

	private static VoidingRequest request(String uf, int year, int series, NumberRange range, String justification) {
		return new VoidingRequest(uf, year, "12345678000190", DocumentModel.NFCE, series, range, justification);
	}

	private static void assertInvalid(String uf, int year, int series, NumberRange range, String justification) {
		assertThrows(IllegalArgumentException.class, () -> request(uf, year, series, range, justification));
	}
}
