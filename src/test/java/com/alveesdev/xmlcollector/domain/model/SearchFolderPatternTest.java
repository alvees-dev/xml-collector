package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SearchFolderPatternTest {

	@Test
	void keepsAPatternWithoutNames() {
		assertEquals("a/b", SearchFolderPattern.fixedPrefix("a/b"));
	}

	@Test
	void stopsAtTheFirstName() {
		assertEquals("a", SearchFolderPattern.fixedPrefix("a/{CNPJ}/b/{AAAAMM}/c"));
	}

	@Test
	void aPatternThatStartsWithANameHasNoPrefix() {
		assertEquals("", SearchFolderPattern.fixedPrefix("{CNPJ}/a"));
	}

	@Test
	void aBlankPatternHasNoPrefix() {
		assertEquals("", SearchFolderPattern.fixedPrefix(""));
	}
}
