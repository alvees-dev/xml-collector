package com.alveesdev.xmlcollector.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class FailuresTest {

	@Test
	void aCheckedExceptionIsAFailureTheProgramExpects() {
		assertFalse(Failures.isDefect(new VoidingException("Informe o certificado")));
		assertFalse(Failures.isDefect(new IOException("Sem rede", new IOException("Tempo esgotado"))));
	}

	@Test
	void anUncheckedExceptionOrErrorIsADefect() {
		assertTrue(Failures.isDefect(new IllegalStateException("estado")));
		assertTrue(Failures.isDefect(new StackOverflowError()));
	}

	@Test
	void aDefectIsFoundBehindACheckedException() {
		assertTrue(Failures.isDefect(new IOException("Falhou", new NullPointerException())));
	}

	@Test
	void aMessageWithoutACauseSaysEverythingWhenItIsExpected() {
		assertFalse(Failures.saysMoreThanItsMessage(new VoidingException("Informe o certificado")));
	}

	@Test
	void aCauseOrADefectSaysMoreThanTheMessage() {
		assertTrue(Failures.saysMoreThanItsMessage(new IOException("Falhou", new IOException("Tempo esgotado"))));
		assertTrue(Failures.saysMoreThanItsMessage(new IllegalStateException("estado")));
	}

	@Test
	void aCauseAlreadyInTheMessageSaysNothingMore() {
		assertFalse(Failures.saysMoreThanItsMessage(new VoidingException("Arquivo não encontrado: C:\\a.pfx", new IOException("C:\\a.pfx"))));
	}

	@Test
	void aCauseThatPointsBackDoesNotHang() {
		IOException first = new IOException("primeiro");
		IOException second = new IOException("segundo", first);
		first.initCause(second);

		assertFalse(Failures.isDefect(first));
	}

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
