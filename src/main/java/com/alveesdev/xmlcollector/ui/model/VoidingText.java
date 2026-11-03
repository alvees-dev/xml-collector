package com.alveesdev.xmlcollector.ui.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public final class VoidingText {

	public static final String CONFIRMATION_WORD = "INUTILIZAR";

	private static final int MAX_LISTED = 15;

	private VoidingText() {
	}

	public static Map<Integer, String> rowLabels(List<VoidingRequest> requests) {
		Map<Integer, String> labels = new LinkedHashMap<>();

		for (int index = 0; index < requests.size(); index++) {
			labels.put(index, rangeLabel(requests.get(index)));
		}

		return labels;
	}

	public static String stage(VoidingProgress.Stage stage) {
		return switch (stage) {
			case SENDING -> "Enviando...";
			case APPROVED -> "OK";
			case REJECTED -> "Recusada";
			case FAILED -> "Falhou";
			case NOT_SENT -> "Não enviada";
		};
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
			text.append("\n\nHomologação é só um teste: não tem efeito fiscal, e os números continuam na lista de faltantes.");
		}

		return text.toString();
	}

	public static String summary(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		StringBuilder text = new StringBuilder();

		if (environment == FiscalEnvironment.HOMOLOGATION) {
			text.append("Teste em homologação, sem efeito fiscal.\n\n");
		}

		for (VoidingOutcome outcome : outcomes) {
			text.append(rangeLabel(outcome.request())).append(": ").append(stage(outcome.stage()));

			if (outcome.detail() != null) {
				text.append(" (").append(outcome.detail()).append(')');
			}

			if (outcome.archiveFailure() != null) {
				text.append("\n  O XML aprovado não foi salvo: ").append(outcome.archiveFailure());
			}

			text.append('\n');
		}

		return text.toString().stripTrailing();
	}

	private static String rangeLabel(VoidingRequest request) {
		NumberRange range = request.range();
		String numbers = range.size() == 1 ? String.valueOf(range.start()) : range.start() + " a " + range.end();
		return "Série " + request.series() + ": " + numbers;
	}
}
