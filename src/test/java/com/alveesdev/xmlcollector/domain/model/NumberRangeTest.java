package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NumberRangeTest {

	@Test
	void aRangeOfOneNumberIsWrittenAsThatNumber() {
		assertEquals("7", new NumberRange(7, 7).text());
		assertEquals(1, new NumberRange(7, 7).size());
	}

	@Test
	void aLongerRangeIsWrittenWithBothEnds() {
		assertEquals("7 a 9", new NumberRange(7, 9).text());
		assertEquals(3, new NumberRange(7, 9).size());
	}

	@Test
	void refusesAStartAfterTheEnd() {
		assertThrows(IllegalArgumentException.class, () -> new NumberRange(9, 7));
	}
}
