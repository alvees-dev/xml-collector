package com.alveesdev.xmlcollector.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class FailuresTest {

	@Test
	void usesTheMessageOfTheError() {
		assertEquals("Disco cheio", Failures.describe(new IOException("Disco cheio")));
	}

	@Test
	void anErrorWithoutMessageIsNamedByItsType() {
		assertEquals("IOException", Failures.describe(new IOException()));
		assertEquals("IOException", Failures.describe(new IOException(" ")));
	}
}
