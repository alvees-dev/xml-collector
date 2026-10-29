package com.alveesdev.xmlcollector.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.model.CopyReport;
import com.alveesdev.xmlcollector.service.CollectionResult;

import javafx.scene.control.Alert;

public final class Dialogs {

	private Dialogs() {
	}

	public static void warning(String message) {
		new Alert(Alert.AlertType.WARNING, message).showAndWait();
	}

	public static void error(String message) {
		new Alert(Alert.AlertType.ERROR, message).showAndWait();
	}

	public static void collectionSummary(CollectionResult result, Path archiveRoot) {

		CopyReport report = result.copyReport();

		List<String> allFailures = new ArrayList<>(result.failures());
		allFailures.addAll(report.getFailures());

		StringBuilder message = new StringBuilder();
		message.append("Copiados: ").append(report.getCopied());
		message.append("\nJá existiam no destino: ").append(report.getAlreadyExisting());
		message.append("\nFora do período: ").append(report.getOutOfPeriod());
		message.append("\nSem data ou ilegíveis: ").append(report.getUnreadable());
		message.append("\n\nDestino: ").append(archiveRoot);

		if (!allFailures.isEmpty()) {
			message.append("\n\nFalhas:\n").append(String.join("\n", allFailures));
		}

		Alert alert = new Alert(
				allFailures.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
				message.toString());
		alert.setHeaderText("Coleta concluída");
		alert.showAndWait();
	}
}