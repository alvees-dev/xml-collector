package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FailureTextTest {

	@Test
	void usesTheMessageOfTheError() {
		assertEquals("Disco cheio", FailureText.of(new IllegalStateException("Disco cheio")));
	}

	@Test
	void anErrorWithoutMessageIsNamedByItsType() {
		assertEquals("IllegalStateException", FailureText.of(new IllegalStateException()));
		assertEquals("IllegalStateException", FailureText.of(new IllegalStateException(" ")));
	}

	@Test
	void aMessageThatWasNotInformedBecomesTheStandardText() {
		assertEquals(FailureText.NO_DESCRIPTION, FailureText.orNoDescription(null));
		assertEquals(FailureText.NO_DESCRIPTION, FailureText.orNoDescription(" "));
		assertEquals("Disco cheio", FailureText.orNoDescription("Disco cheio"));
	}

	@Test
	void anUnexpectedErrorSaysWhereTheDetailsAre() {
		String text = FailureText.unexpected(new IllegalStateException("Disco cheio"), Path.of("Logs"));

		assertTrue(text.contains("Disco cheio"), text);
		assertTrue(text.endsWith("Logs"), text);
	}
}
