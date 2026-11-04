package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Stage;

class DispatchProgressTextTest {

	@Test
	void listsTheStepsInOrder() {
		assertEquals(
				List.of("Compactando arquivos", "Salvando no Google Drive", "Enviando e-mail"),
				List.copyOf(DispatchProgressText.stepLabels().values()));
	}

	@Test
	void describesEachStage() {
		assertEquals("Em andamento...", DispatchProgressText.of(Stage.RUNNING));
		assertEquals("OK", DispatchProgressText.of(Stage.DONE));
		assertEquals("Não necessário", DispatchProgressText.of(Stage.SKIPPED));
	}
}
