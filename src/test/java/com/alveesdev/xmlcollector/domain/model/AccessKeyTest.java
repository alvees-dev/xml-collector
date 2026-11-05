package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AccessKeyTest {

	private static final String KEY = "35" + "26" + "01" + "12345678000190" + "65" + "007" + "000000123" + "1" + "000000456";
	private static final String ALPHANUMERIC_CNPJ = "12ABC34501DE35";
	private static final String ALPHANUMERIC_KEY = "35" + "26" + "01" + ALPHANUMERIC_CNPJ + "65" + "007" + "000000123" + "1" + "000000456";

	@Test
	void splitsTheKeyIntoItsParts() {
		AccessKey key = new AccessKey(KEY);

		assertEquals("35", key.ufCode());
		assertEquals(2026, key.year());
		assertEquals("12345678000190", key.cnpj());
		assertEquals("65", key.modelCode());
		assertEquals(7, key.series());
		assertEquals(123, key.number());
	}

	@Test
	void knowsTheModelOfTheDocument() {
		assertEquals(Optional.of(DocumentModel.NFCE), new AccessKey(KEY).model());
	}

	@Test
	void anUnknownModelCodeHasNoModel() {
		String otherModel = KEY.substring(0, 20) + "99" + KEY.substring(22);

		assertEquals(Optional.empty(), new AccessKey(otherModel).model());
	}

	@Test
	void refusesAKeyWithoutExactly44Characters() {
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(null));
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(KEY.substring(1)));
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(KEY + "0"));
	}

	@Test
	void findsTheKeyInsideAFileName() {
		assertEquals(Optional.of(new AccessKey(KEY)), AccessKey.findIn("NFe" + KEY + "-nfe.xml"));
	}

	@Test
	void doesNotTakePartOfALongerNumberAsAKey() {
		assertEquals(Optional.empty(), AccessKey.findIn("1" + KEY));
		assertEquals(Optional.empty(), AccessKey.findIn(KEY + "1"));
	}

	@Test
	void findsNothingInATextWithoutAKey() {
		assertEquals(Optional.empty(), AccessKey.findIn("nota-" + KEY.substring(1) + ".xml"));
	}

	@Test
	void aKeyCanHaveAnAlphanumericCnpj() {
		AccessKey key = new AccessKey(ALPHANUMERIC_KEY);

		assertEquals(ALPHANUMERIC_CNPJ, key.cnpj());
		assertEquals(7, key.series());
		assertEquals(123, key.number());
	}

	@Test
	void findsAnAlphanumericKeyInsideAFileName() {
		assertEquals(Optional.of(new AccessKey(ALPHANUMERIC_KEY)), AccessKey.findIn("NFe" + ALPHANUMERIC_KEY + "-nfe.xml"));
		assertEquals(Optional.of(new AccessKey(ALPHANUMERIC_KEY)), AccessKey.findIn("NFE" + ALPHANUMERIC_KEY + "-NFE.XML"));
	}

	@Test
	void theCheckDigitsOfTheCnpjStayNumeric() {
		String withLetters = "352601" + "12ABC34501DEAB" + KEY.substring(20);

		assertThrows(IllegalArgumentException.class, () -> new AccessKey(withLetters));
	}

	@Test
	void lettersOnlyGoInTheCnpj() {
		assertThrows(IllegalArgumentException.class, () -> new AccessKey("A" + KEY.substring(1)));
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(KEY.substring(0, 25) + "A" + KEY.substring(26)));
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(KEY.substring(0, 43) + "A"));
	}

	@Test
	void lettersOfTheCnpjAreCapital() {
		assertThrows(IllegalArgumentException.class, () -> new AccessKey(ALPHANUMERIC_KEY.toLowerCase()));
	}

}
