package com.alveesdev.xmlcollector.ui.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.settings.Theme;

class ThemesTest {

	@Test
	void everyThemeHasItsStylesheet() {
		for (Theme theme : Theme.values()) {
			assertNotNull(Themes.stylesheetOf(theme), theme.label());
		}
	}

	@Test
	void onlyNoiteXmlIsDark() {
		assertTrue(Theme.NIGHT.isDark());
		assertFalse(Theme.DEEP_BLUE.isDark());
		assertFalse(Theme.DISCREET.isDark());
	}
}
