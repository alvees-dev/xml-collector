package com.alveesdev.xmlcollector.application.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThemeTest {

	@Test
	void theSunGoesBetweenTheLightAndTheDarkTheme() {
		assertEquals(Theme.NIGHT, Theme.DEEP_BLUE.toggled());
		assertEquals(Theme.DEEP_BLUE, Theme.NIGHT.toggled());
	}

	@Test
	void fromDiscretoTheSunGoesToTheDarkTheme() {
		assertEquals(Theme.NIGHT, Theme.DISCREET.toggled());
	}
}
