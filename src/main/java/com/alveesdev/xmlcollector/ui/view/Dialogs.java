package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.ui.model.CollectionSummaryText;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.MessageLayout;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.model.VoidingText;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class Dialogs {

	public enum ExistingZipChoice {
		REPLACE,
		SEND_EXISTING,
		CANCEL
	}

	private static final double MESSAGE_WIDTH = 560;
	private static final double WHOLE_MESSAGE_WIDTH = 760;
	private static final String MESSAGE_STYLE = "-fx-control-inner-background: -fx-background; -fx-background-color: transparent;"
			+ " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

	private Dialogs() {
	}

	public static void info(String message) {
		alert(Alert.AlertType.INFORMATION, message).showAndWait();
	}

	public static void warning(String message) {
		alert(Alert.AlertType.WARNING, message).showAndWait();
	}

	public static void error(String message) {
		alert(Alert.AlertType.ERROR, message).showAndWait();
	}

	// The label of a plain Alert cuts a long message off without any way of reading the rest.
	private static Alert alert(Alert.AlertType type, String message, ButtonType... buttons) {
		String text = FailureText.orNoDescription(message);

		return alertWith(previewOf(text), type, text, buttons);
	}

	// For what must be read before answering: the end of the text can hold the warning or the word to type.
	private static Alert wholeMessageAlert(Alert.AlertType type, String message, ButtonType... buttons) {
		return alertWith(messageArea(message), type, message, buttons);
	}

	private static Alert alertWith(Node content, Alert.AlertType type, String message, ButtonType... buttons) {
		Alert alert = new Alert(type, message, buttons);
		alert.getDialogPane().setContent(content);
		alert.getDialogPane().setPrefWidth(MESSAGE_WIDTH);
		return alert;
	}

	private static Node previewOf(String message) {

		if (!MessageLayout.isLong(message)) {
			return messageArea(message);
		}

		Button seeAll = new Button("Ver tudo");
		seeAll.setFocusTraversable(false);
		seeAll.setOnAction(event -> showWholeMessage(message, seeAll.getScene().getWindow()));

		return new VBox(8, messageArea(MessageLayout.preview(message)), seeAll);
	}

	private static void showWholeMessage(String message, Window owner) {
		ButtonType close = new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.NONE, message, close);
		alert.initOwner(owner);
		alert.setTitle("Mensagem completa");
		alert.getDialogPane().setContent(messageArea(message));
		alert.getDialogPane().setPrefWidth(WHOLE_MESSAGE_WIDTH);
		alert.showAndWait();
	}

	private static TextArea messageArea(String message) {
		TextArea area = new TextArea(message);
		area.setEditable(false);
		area.setWrapText(true);
		area.setFocusTraversable(false);
		// One row more, because the height of exactly that many rows leaves a scroll bar over the text.
		area.setPrefRowCount(MessageLayout.rowsFor(message) + 1);
		area.setStyle(MESSAGE_STYLE);
		return area;
	}

	public static void collectionSummary(CollectionResult result, Path archiveRoot, SourceMode sourceMode, String header) {

		Alert alert = alert(
				CollectionSummaryText.collectionHasProblems(result, sourceMode) ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				CollectionSummaryText.collection(result, archiveRoot, sourceMode));
		alert.setHeaderText(header);
		alert.showAndWait();
	}

	public static ExistingZipChoice askAboutExistingZip() {

		ButtonType replace = new ButtonType("Sim", ButtonBar.ButtonData.YES);
		ButtonType cancel = new ButtonType("Cancelar envio", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType sendExisting = new ButtonType("Enviar arquivo existente", ButtonBar.ButtonData.OTHER);

		Alert alert = alert(Alert.AlertType.CONFIRMATION,
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

		Alert alert = alert(Alert.AlertType.WARNING,
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
			TextArea area = messageArea(text);
			VBox.setVgrow(area, Priority.ALWAYS);
			dialog.getDialogPane().setContent(new VBox(10, area, dialog.getEditor()));
			dialog.getDialogPane().setPrefWidth(MESSAGE_WIDTH);

			return dialog.showAndWait().map(VoidingText.CONFIRMATION_WORD::equals).orElse(false);
		}

		ButtonType send = new ButtonType("Enviar teste", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = wholeMessageAlert(Alert.AlertType.CONFIRMATION, text, cancel, send);
		alert.setHeaderText("Confirme a inutilização em homologação");
		((Button) alert.getDialogPane().lookupButton(send)).setDefaultButton(false);
		((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

		return alert.showAndWait().filter(send::equals).isPresent();
	}

	public static void voidingSummary(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		boolean allApproved = outcomes.stream().allMatch(outcome -> outcome.stage() == VoidingProgress.Stage.APPROVED && outcome.archiveFailure() == null && outcome.pdvFailures().isEmpty());

		Alert alert = alert(allApproved ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, VoidingText.summary(outcomes, environment));
		alert.setHeaderText("Solicitação concluída");
		alert.showAndWait();
	}

	public static void missingSearchSummary(CollectionResult result, List<XmlDocument.Invoice> found, Path archiveRoot) {

		Alert alert = alert(
				CollectionSummaryText.missingSearchHasProblems(result) ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				CollectionSummaryText.missingSearch(result, found, archiveRoot));
		alert.setHeaderText("Busca de notas faltantes concluída");
		alert.showAndWait();
	}
}