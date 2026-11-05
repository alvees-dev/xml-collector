package com.alveesdev.xmlcollector.ui.model;

import java.util.List;

import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public final class VoidingText {

	public static final String CONFIRMATION_WORD = "INUTILIZAR";

	private static final int MAX_LISTED = 15;

	private VoidingText() {
	}

	public static String environment(FiscalEnvironment environment) {
		return environment == FiscalEnvironment.PRODUCTION ? "Produção" : "Homologação";
	}

	public static String confirmation(List<VoidingRequest> requests, FiscalEnvironment environment) {

		int total = requests.stream().map(VoidingRequest::range).mapToInt(NumberRange::size).sum();

		StringBuilder text = new StringBuilder();
		text.append("Ambiente: ").append(environment(environment).toUpperCase());
		text.append("\nCNPJ: ").append(requests.get(0).cnpj());
		text.append("\nNúmeros a inutilizar: ").append(total).append('\n');

		requests.stream().limit(MAX_LISTED).forEach(request -> text.append('\n').append(rangeLabel(request)));

		if (requests.size() > MAX_LISTED) {
			text.append("\n... e mais ").append(requests.size() - MAX_LISTED).append(" faixa(s)");
		}

		text.append("\n\nJustificativa: ").append(requests.get(0).justification());

		if (environment == FiscalEnvironment.PRODUCTION) {
			text.append("\n\nEsta operação NÃO pode ser desfeita. Confira se nenhuma dessas notas está em contingência.");
			text.append("\nPara continuar, digite ").append(CONFIRMATION_WORD).append(':');
		} else {
			text.append("\n\nHomologação é só um teste: não tem efeito fiscal. A inutilização de arquivos não será efetivada. Mude para ambiente de Produção para Inutilizar.");
		}

		return text.toString();
	}

	public static String summary(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		StringBuilder text = new StringBuilder();

		if (environment == FiscalEnvironment.HOMOLOGATION) {
			text.append("Teste em homologação, sem efeito fiscal.");
		}

		if (outcomes.stream().allMatch(outcome -> outcome.stage() == Stage.APPROVED)) {
			startBlock(text);
			text.append(environment == FiscalEnvironment.PRODUCTION
					? "As numerações foram inutilizadas."
					: "Teste concluído, nenhuma numeração foi inutilizada.");
		}

		List<VoidingOutcome> rejected = inStage(outcomes, Stage.REJECTED);

		if (!rejected.isEmpty()) {
			startBlock(text);
			text.append("Numerações recusadas:");
			rejected.forEach(outcome -> text.append('\n').append(rangeLabel(outcome.request())));
		}

		// The answer is unknown, so they may have been voided anyway.
		List<VoidingOutcome> failed = inStage(outcomes, Stage.FAILED);

		if (!failed.isEmpty()) {
			startBlock(text);
			text.append("Falharam (a resposta da Sefaz é desconhecida, confira antes de reenviar):");
			failed.forEach(outcome -> text.append('\n').append(rangeLabel(outcome.request())).append(" (").append(outcome.detail()).append(')'));
		}

		List<VoidingOutcome> notSent = inStage(outcomes, Stage.NOT_SENT);

		if (!notSent.isEmpty()) {
			startBlock(text);
			text.append("Não enviadas:");
			notSent.forEach(outcome -> text.append('\n').append(rangeLabel(outcome.request())));
		}

		appendSaveFailures(text, outcomes);

		return text.toString();
	}

	private static void startBlock(StringBuilder text) {
		if (!text.isEmpty()) {
			text.append("\n\n");
		}
	}

	private static List<VoidingOutcome> inStage(List<VoidingOutcome> outcomes, Stage stage) {
		return outcomes.stream().filter(outcome -> outcome.stage() == stage).toList();
	}

	private static void appendSaveFailures(StringBuilder text, List<VoidingOutcome> outcomes) {

		for (VoidingOutcome outcome : outcomes) {
			for (String failure : outcome.pdvFailures()) {
				text.append("\n\n").append(rangeLabel(outcome.request()));
				text.append("\nATENÇÃO: o XML NÃO foi salvo na pasta do PDV. Copie-o de Inutilizadas: ").append(failure);
			}

			if (outcome.archiveFailure() != null) {
				text.append("\n\n").append(rangeLabel(outcome.request()));
				text.append("\nO XML aprovado não foi salvo: ").append(outcome.archiveFailure());
			}
		}
	}

	private static String rangeLabel(VoidingRequest request) {
		return "Série " + request.series() + ": " + request.range().text();
	}
}
