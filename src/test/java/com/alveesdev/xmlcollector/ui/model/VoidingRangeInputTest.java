package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VoidingRangeInputTest {

	@Test
	void anEmptyFinalNumberTakesTheInitialOne() {
		assertEquals("1234", VoidingRangeInput.endAfterStartChanged("", "1234", ""));
	}

	@Test
	void theFinalNumberFollowsWhileTheInitialOneIsTyped() {
		assertEquals("123", VoidingRangeInput.endAfterStartChanged("12", "123", "12"));
	}

	@Test
	void aFinalNumberWrittenByTheUserStays() {
		assertEquals("1300", VoidingRangeInput.endAfterStartChanged("1234", "1235", "1300"));
	}

	@Test
	void erasingTheInitialNumberAlsoErasesTheOneThatFollowedIt() {
		assertEquals("", VoidingRangeInput.endAfterStartChanged("1", "", "1"));
	}
}
