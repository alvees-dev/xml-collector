package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.application.CollectionResult;
import com.alveesdev.xmlcollector.application.CollectionSummary;
import com.alveesdev.xmlcollector.application.SourceFailure;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;

import javafx.scene.control.Alert;

public final class Dialogs {

	private static final int MAX_LISTED_FAILURES = 15;

	private Dialogs() {
	}

	public static void warning(String message) {
		new Alert(Alert.AlertType.WARNING, message).showAndWait();
	}

	public static void error(String message) {
		new Alert(Alert.AlertType.ERROR, message).showAndWait();
	}

	public static void collectionSummary(CollectionResult result, Path archiveRoot, boolean pdvMode) {

		CollectionSummary summary = result.summary();
		List<String> failures = describeFailures(result, pdvMode);

		StringBuilder message = new StringBuilder();
		message.append("Copiados: ").append(summary.copied());

		if (summary.renamed() > 0) {
			message.append("\nRenomeados pela chave (nome repetido): ").append(summary.renamed());
		}

		if (summary.replaced() > 0) {
			message.append("\nSubstituídos (arquivo danificado no destino): ").append(summary.replaced());
		}

		message.append("\nJá existiam no destino: ").append(summary.alreadyExisting());
		message.append("\nDuplicados (mesma chave): ").append(summary.duplicates());
		message.append("\nFora do período: ").append(summary.outOfPeriod());
		message.append("\nIgnorados (não são NFCe): ").append(summary.ignored());
		message.append("\nCom erro de leitura: ").append(summary.errors());
		message.append("\n\nDestino: ").append(archiveRoot);

		if (!failures.isEmpty()) {
			message.append("\n\nFalhas:\n").append(String.join("\n", failures));
		}

		boolean hasProblems = !failures.isEmpty() || summary.errors() > 0;

		Alert alert = new Alert(
				hasProblems ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				message.toString());
		alert.setHeaderText("Coleta concluída");
		alert.showAndWait();
	}

	private static List<String> describeFailures(CollectionResult result, boolean pdvMode) {

		List<String> lines = new ArrayList<>();

		for (SourceFailure failure : result.failures()) {
			String label = SourceLabels.label(failure.sourceIndex(), pdvMode);

			lines.add(switch (failure.reason()) {
				case NOT_INFORMED -> label + ": diretório não informado";
				case INACCESSIBLE -> label + ": diretório inacessível (" + failure.path() + ")";
				case SUBFOLDER_INACCESSIBLE -> label + ": subpasta inacessível (" + failure.path() + ")";
			});
		}

		for (CollectionSummary.CopyFailure failure : result.summary().copyFailures()) {
			lines.add("Falha ao copiar " + failure.fileName() + ": " + failure.reason());
		}

		if (lines.size() > MAX_LISTED_FAILURES) {
			int hidden = lines.size() - MAX_LISTED_FAILURES;
			List<String> limited = new ArrayList<>(lines.subList(0, MAX_LISTED_FAILURES));
			limited.add("... e mais " + hidden + " falha(s)");
			return limited;
		}

		return lines;
	}
}