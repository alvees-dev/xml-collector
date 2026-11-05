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
	void describesTheRangesOfEachSeriesInOrder() {
		assertEquals("série 1: 7, 10 a 12; série 2: 5",
				VoidingText.ranges(Map.of(2, List.of(new NumberRange(5, 5)), 1, List.of(new NumberRange(7, 7), new NumberRange(10, 12)))));
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
	void theReportListsOnlyTheRefusedNumbers() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", null, List.of(), null),
				new VoidingOutcome(RANGE, Stage.REJECTED, "241 - Numero ja utilizado", null, List.of(), null)), FiscalEnvironment.PRODUCTION);

		assertEquals("Numerações recusadas:\nSérie 2: 10 a 12", text);
	}

	@Test
	void theReportSaysTheNumbersWereVoidedWhenAllWereApproved() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", null, List.of(), null)), FiscalEnvironment.PRODUCTION);

		assertEquals("As numerações foram inutilizadas.", text);
	}

	@Test
	void theReportDoesNotClaimTheNumbersWereVoidedWhenTheSendingFailed() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.FAILED, "sem resposta")), FiscalEnvironment.PRODUCTION);

		assertEquals("Falharam (a resposta da Sefaz é desconhecida, confira antes de reenviar):\nSérie 1: 7 (sem resposta)", text);
	}

	@Test
	void theReportDoesNotClaimTheNumbersWereVoidedWhenTheyWereNotSent() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.NOT_SENT, null)), FiscalEnvironment.PRODUCTION);

		assertEquals("Não enviadas:\nSérie 1: 7", text);
	}

	@Test
	void anApprovedTestInHomologationDoesNotClaimTheNumbersWereVoided() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", null, List.of(), null)), FiscalEnvironment.HOMOLOGATION);

		assertEquals("Teste em homologação, sem efeito fiscal.\n\nTeste concluído, nenhuma numeração foi inutilizada.", text);
	}

	@Test
	void theReportDoesNotHideWhatStillNeedsAttention() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", "disco cheio", List.of("pdv1: sem acesso"), null),
				new VoidingOutcome(RANGE, Stage.FAILED, "sem resposta"),
				new VoidingOutcome(request(3, 5, 5), Stage.NOT_SENT, null)), FiscalEnvironment.PRODUCTION);

		assertTrue(text.contains("não foi salvo: disco cheio"));
		assertTrue(text.contains("NÃO foi salvo na pasta do PDV"));
		assertTrue(text.contains("pdv1: sem acesso"));
		assertTrue(text.contains("Série 2: 10 a 12 (sem resposta)"));
		assertTrue(text.contains("Não enviadas:\nSérie 3: 5"));
	}

	@Test
	void theReportWarnsThatHomologationIsATest() {
		String text = VoidingText.summary(List.of(
				new VoidingOutcome(SINGLE, Stage.APPROVED, "102 - Homologado", null, List.of(), null)), FiscalEnvironment.HOMOLOGATION);

		assertTrue(text.startsWith("Teste em homologação"));
	}

	private static VoidingRequest request(int series, int start, int end) {
		return new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, series, new NumberRange(start, end),
				VoidingRequest.STANDARD_JUSTIFICATION);
	}

	@Test
	void warnsThatAnApprovedVoidingHasNoCopy() {
		VoidingRequest request = new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, 1, new NumberRange(5, 7), VoidingRequest.STANDARD_JUSTIFICATION);
		VoidingOutcome outcome = new VoidingOutcome(request, Stage.APPROVED, "102 - ok", "disco cheio", List.of(), "<xml/>");

		String summary = VoidingText.summary(List.of(outcome), FiscalEnvironment.PRODUCTION);
		String unsaved = VoidingText.unsavedXml(outcome, "disco cheio");

		assertTrue(summary.contains("não há cópia do XML"), summary);
		assertTrue(unsaved.contains("Série 1: 5 a 7"), unsaved);
		assertTrue(unsaved.contains("disco cheio"), unsaved);
		assertTrue(unsaved.endsWith("<xml/>"), unsaved);
	}

}
