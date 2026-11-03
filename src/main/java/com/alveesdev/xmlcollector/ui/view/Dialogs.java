package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;

import javafx.scene.control.Alert;

public final class Dialogs {

	private static final int MAX_LISTED_FAILURES = 15;

	private Dialogs() {
	}

	public static void info(String message) {
		new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
	}

	public static void warning(String message) {
		new Alert(Alert.AlertType.WARNING, message).showAndWait();
	}

	public static void error(String message) {
		new Alert(Alert.AlertType.ERROR, message).showAndWait();
	}

	public static void collectionSummary(CollectionResult result, Path archiveRoot, SourceMode sourceMode) {

		CollectionSummary summary = result.summary();
		List<String> failures = describeFailures(result, sourceMode);

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

	public static void missingSearchSummary(CollectionResult result, List<XmlDocument.Invoice> found, Path archiveRoot) {

		List<String> failures = describeFailures(result, SourceMode.PDV_DIRECTORIES);

		StringBuilder message = new StringBuilder();
		message.append("Encontradas: ").append(found.size());

		found.stream()
				.sorted(Comparator.comparingInt(XmlDocument.Invoice::series).thenComparingInt(XmlDocument.Invoice::number))
				.limit(MAX_LISTED_FAILURES)
				.forEach(invoice -> message.append("\nSérie ").append(invoice.series()).append(": ").append(invoice.number()));

		if (found.size() > MAX_LISTED_FAILURES) {
			message.append("\n... e mais ").append(found.size() - MAX_LISTED_FAILURES);
		}

		message.append("\n\nCom erro de leitura: ").append(result.summary().errors());
		message.append("\nDestino: ").append(archiveRoot);

		if (!failures.isEmpty()) {
			message.append("\n\nFalhas:\n").append(String.join("\n", failures));
		}

		Alert alert = new Alert(
				failures.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
				message.toString());
		alert.setHeaderText("Busca de notas faltantes concluída");
		alert.showAndWait();
	}

	private static List<String> describeFailures(CollectionResult result, SourceMode sourceMode) {

		List<String> lines = new ArrayList<>();

		for (SourceFailure failure : result.failures()) {
			String label = SourceLabels.label(failure.sourceIndex(), sourceMode);

			lines.add(switch (failure.reason()) {
				case NOT_INFORMED -> label + ": diretório não informado";
				case INACCESSIBLE -> label + ": diretório inacessível (" + failure.path() + ")";
				case NO_MODEL_FOLDER -> label + ": nenhuma pasta do modelo de busca encontrada em " + failure.path();
				case SUBFOLDER_INACCESSIBLE -> label + ": subpasta inacessível (" + failure.path() + ")";
			});
		}

		if (result.historyFailure() != null) {
			lines.add(result.historyFailure());
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