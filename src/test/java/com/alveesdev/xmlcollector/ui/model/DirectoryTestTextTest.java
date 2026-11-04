package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult;
import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult.Outcome;

class DirectoryTestTextTest {

	@Test
	void describesEachFailure() {
		assertEquals("Diretório não informado", DirectoryTestText.of(DirectoryTestResult.of(Outcome.NOT_INFORMED)));
		assertEquals("Sem conexão ou diretório não encontrado", DirectoryTestText.of(DirectoryTestResult.of(Outcome.NO_CONNECTION)));
		assertEquals("Sem permissão de leitura", DirectoryTestText.of(DirectoryTestResult.of(Outcome.NO_PERMISSION)));
		assertEquals("Conectou, mas a busca das pastas demorou", DirectoryTestText.of(DirectoryTestResult.of(Outcome.SEARCH_TOO_SLOW)));
	}

	@Test
	void aConnectionWithoutProblemsIsOk() {
		assertEquals("OK", DirectoryTestText.of(DirectoryTestResult.of(Outcome.CONNECTED)));
	}

	@Test
	void listsEveryWarningOfAConnection() {
		DirectoryTestResult result = new DirectoryTestResult(Outcome.CONNECTED, true, List.of("a", "b"), false);

		assertEquals("Conectou, mas falta a pasta a\n"
				+ "Conectou, mas falta a pasta b\n"
				+ "Conectou, mas sem permissão de gravação", DirectoryTestText.of(result));
	}

	@Test
	void missingFoldersReplaceTheGeneralMessage() {
		DirectoryTestResult result = new DirectoryTestResult(Outcome.CONNECTED, false, List.of("a"), true);

		assertEquals("Conectou, mas falta a pasta a", DirectoryTestText.of(result));
	}

	@Test
	void aModelWithOnlyOptionalFoldersGetsTheGeneralMessage() {
		DirectoryTestResult result = new DirectoryTestResult(Outcome.CONNECTED, false, List.of(), true);

		assertEquals("Conectou, mas nenhuma pasta do modelo foi encontrada", DirectoryTestText.of(result));
	}
}
