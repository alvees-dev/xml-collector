package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class VoidingTextTest {

	private static final VoidingRequest SINGLE = request(1, 7, 7);
	private static final VoidingRequest RANGE = request(2, 10, 12);

	@Test
	void labelsARowByItsSeriesAndNumbers() {
		assertEquals(Map.of(0, "Série 1: 7", 1, "Série 2: 10 a 12"), VoidingText.rowLabels(List.of(SINGLE, RANGE)));
	}

	@Test
	void theConfirmationShowsTheTotalAndAsksForTheWordInProduction() {
		String text = VoidingText.confirmation(List.of(SINGLE, RANGE), FiscalEnvironment.PRODUCTION);

		assertTrue(text.contains("PRODUÇÃO"));
		assertTrue(text.contains("Números a inutilizar: 4"));
		assertTrue(text.contains("Série 2: 10 a 12"));
		assertTrue(text.contains("12345678000190"));
		assertTrue(text.contains("digite INUTILIZAR"));
	}

	@Test
	void theConfirmationExplainsThatHomologationIsATest() {
		String text = VoidingText.confirmation(List.of(SINGLE), FiscalEnvironment.HOMOLOGATION);

		assertTrue(text.contains("HOMOLOGAÇÃO"));
		assertTrue(text.contains("sem efeito") || text.contains("não tem efeito fiscal"));
		assertFalse(text.contains("digite"));
	}

	@Test
	void theSummaryNamesEachResultAndAnArchiveFailure() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", "disco cheio"),
				new VoidingOutcome(RANGE, Stage.NOT_SENT, null, null)), FiscalEnvironment.PRODUCTION);

		assertTrue(text.contains("Série 1: 7: OK (102 - Homologado)"));
		assertTrue(text.contains("não foi salvo: disco cheio"));
		assertTrue(text.contains("Série 2: 10 a 12: Não enviada"));
		assertFalse(text.contains("homologação"));
	}

	private static VoidingRequest request(int series, int start, int end) {
		return new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, series, new NumberRange(start, end),
				VoidingRequest.STANDARD_JUSTIFICATION);
	}
}
