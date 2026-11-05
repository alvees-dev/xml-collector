package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.port.DispatchException;

class DispatchTextTest {

	private static final long THREE_AND_A_HALF_MEGABYTES = 3_670_016;

	@Test
	void anAttachedFileShowsTheSizeInTheBrazilianWay() {
		DispatchResult result = new DispatchResult(DispatchResult.Method.ATTACHED, List.of(), THREE_AND_A_HALF_MEGABYTES, null, null);

		assertEquals("E-mail enviado para contador@exemplo.com com o XML de Janeiro de 2026 em anexo (3,5 MB).",
				DispatchText.success("Janeiro de 2026", "contador@exemplo.com", result));
	}

	@Test
	void aLinkToTheDriveIsShown() {
		DispatchResult result = new DispatchResult(DispatchResult.Method.DRIVE_LINK, List.of(), THREE_AND_A_HALF_MEGABYTES, "http://exemplo/pasta", null);

		String text = DispatchText.success("Janeiro de 2026", "contador@exemplo.com", result);

		assertTrue(text.contains("passou do limite de 25 MB"), text);
		assertTrue(text.contains("foi enviado para contador@exemplo.com"), text);
		assertTrue(text.endsWith("Link: http://exemplo/pasta"), text);
		assertFalse(text.contains("Atenção"), text);
	}

	@Test
	void warnsWhenThePreviousFileStayedInTheDrive() {
		DispatchResult result = new DispatchResult(DispatchResult.Method.DRIVE_LINK, List.of(), THREE_AND_A_HALF_MEGABYTES, "http://exemplo/pasta", "sem permissão");

		String text = DispatchText.success("Janeiro de 2026", "contador@exemplo.com", result);

		assertTrue(text.contains("Atenção: o arquivo anterior não foi removido"), text);
		assertTrue(text.endsWith("Motivo: sem permissão"), text);
	}

	@Test
	void aKnownFailureShowsItsOwnMessage() {
		assertEquals("Senha incorreta", DispatchText.failure(new DispatchException("Senha incorreta")));
	}

	@Test
	void anUnexpectedFailureIsIdentifiedEvenWithoutMessage() {
		assertEquals("Erro inesperado ao enviar o e-mail: falhou", DispatchText.failure(new IllegalStateException("falhou")));
		assertEquals("Erro inesperado ao enviar o e-mail: IllegalStateException", DispatchText.failure(new IllegalStateException()));
	}
}
