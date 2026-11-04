package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.model.VoidingText;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

public final class Dialogs {

	public enum ExistingZipChoice {
		REPLACE,
		SEND_EXISTING,
		CANCEL
	}

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

	public static void collectionSummary(CollectionResult result, Path archiveRoot, SourceMode sourceMode, String header) {

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
		message.append("\nFora do período: ").append(summary.outOfPeriod());
		message.append("\nCom erro de leitura: ").append(summary.errors());
		message.append("\n\nArquivos salvos em: ").append(archiveRoot);

		if (!failures.isEmpty()) {
			message.append("\n\nFalhas:\n").append(String.join("\n", failures));
		}

		boolean hasProblems = !failures.isEmpty() || summary.errors() > 0;

		Alert alert = new Alert(
				hasProblems ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				message.toString());
		alert.setHeaderText(header);
		alert.showAndWait();
	}

	public static ExistingZipChoice askAboutExistingZip() {

		ButtonType replace = new ButtonType("Sim", ButtonBar.ButtonData.YES);
		ButtonType cancel = new ButtonType("Cancelar envio", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType sendExisting = new ButtonType("Enviar arquivo existente", ButtonBar.ButtonData.OTHER);

		Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
				"Foi encontrado um arquivo compactado. Gostaria de compactar um novo e substituir ou enviar o existente?"
						+ "\n\nO arquivo existente pode estar desatualizado.",
				replace, cancel, sendExisting);
		alert.setHeaderText("Arquivo compactado encontrado");

		ButtonType chosen = alert.showAndWait().orElse(cancel);

		if (chosen == replace) {
			return ExistingZipChoice.REPLACE;
		}

		return chosen == sendExisting ? ExistingZipChoice.SEND_EXISTING : ExistingZipChoice.CANCEL;
	}

	public static boolean confirmVoidingWithoutSearch() {

		ButtonType yes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.WARNING,
				"Podem existir notas efetivadas entre as faltantes. Tem certeza que quer INUTILIZAR antes de Procurar Todas?",
				cancel, yes);
		((Button) alert.getDialogPane().lookupButton(yes)).setDefaultButton(false);
		((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

		return alert.showAndWait().filter(yes::equals).isPresent();
	}

	public static boolean confirmVoiding(List<VoidingRequest> requests, FiscalEnvironment environment) {

		String text = VoidingText.confirmation(requests, environment);

		if (environment == FiscalEnvironment.PRODUCTION) {
			TextInputDialog dialog = new TextInputDialog();
			dialog.setTitle("Inutilizar notas");
			dialog.setHeaderText("Confirme a inutilização em PRODUÇÃO");
			dialog.getDialogPane().setContent(new VBox(10, new Label(text), dialog.getEditor()));

			return dialog.showAndWait().map(VoidingText.CONFIRMATION_WORD::equals).orElse(false);
		}

		ButtonType send = new ButtonType("Enviar teste", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, cancel, send);
		alert.setHeaderText("Confirme a inutilização em homologação");
		((Button) alert.getDialogPane().lookupButton(send)).setDefaultButton(false);
		((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

		return alert.showAndWait().filter(send::equals).isPresent();
	}

	public static void voidingSummary(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		boolean allApproved = outcomes.stream().allMatch(outcome -> outcome.stage() == VoidingProgress.Stage.APPROVED && outcome.archiveFailure() == null && outcome.pdvFailures().isEmpty());

		Alert alert = new Alert(allApproved ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, VoidingText.summary(outcomes, environment));
		alert.setHeaderText("Solicitação concluída");
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
				case NO_MODEL_FOLDER -> label + ": nenhuma pasta do modelo de busca encontrada (" + failure.path() + ")";
				case FOLDER_NOT_FOUND -> label + ": pasta não encontrada (" + failure.path() + ")";
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