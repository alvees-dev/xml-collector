package com.alveesdev.xmlcollector.application.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AppVersionTest {

	@Test
	void theBuildPutsTheVersionOfThePomInTheProgram() {
		assertEquals("1.0.0", AppVersion.current());
	}

	@Test
	void aFileThatWasNotFilledByTheBuildSaysTheVersionIsUnknown() throws Exception {
		assertEquals(AppVersion.UNKNOWN, AppVersion.read(input("version=${project.version}")));
		assertEquals(AppVersion.UNKNOWN, AppVersion.read(input("")));
	}

	@Test
	void readsTheVersionWithoutSpaces() throws Exception {
		assertEquals("1.2.3", AppVersion.read(input("version= 1.2.3 ")));
	}

	private static ByteArrayInputStream input(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
	}
}
