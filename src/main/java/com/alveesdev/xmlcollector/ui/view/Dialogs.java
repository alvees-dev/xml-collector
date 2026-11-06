package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
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
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.stage.Window;

public final class Dialogs {

	public enum ExistingZipChoice {
		REPLACE,
		SEND_EXISTING,
		CANCEL
	}

	private static final String DISPATCH_COMPLETED = "Envio Concluído";
	private static final String CHECK_SHAPE = "M 0 7 L 6 13 L 17 1";
	private static final double CHECK_RADIUS = 18;
	private static final Color CHECK_GREEN = Color.web("#2e7d32");
	private static final double MESSAGE_WIDTH = 560;
	private static final double MIN_TEXT_WIDTH = 170;
	private static final double MAX_TEXT_WIDTH = 500;
	private static final double ROUNDING_OF_THE_TEXT = 4;
	private static final double AROUND_THE_TEXT = 40;
	private static final double ICON_BESIDE_THE_TEXT = 64;
	private static final double WHOLE_MESSAGE_WIDTH = 760;
	private static final String MESSAGE_STYLE = "-fx-control-inner-background: -fx-background; -fx-background-color: transparent;"
			+ " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

	private Dialogs() {
	}

	public static void info(String message) {
		show(Alert.AlertType.INFORMATION, message, message);
	}

	public static void warning(String message) {
		show(Alert.AlertType.WARNING, message, message);
	}

	public static void error(String message) {
		show(Alert.AlertType.ERROR, message, message);
	}

	// The function goes only to the log, because on the screen the person already knows what was just asked.
	public static void info(String function, String message) {
		show(Alert.AlertType.INFORMATION, function + ": " + FailureText.orNoDescription(message), message);
	}

	public static void warning(String function, String message) {
		show(Alert.AlertType.WARNING, function + ": " + FailureText.orNoDescription(message), message);
	}

	public static void error(String function, String message) {
		show(Alert.AlertType.ERROR, function + ": " + FailureText.orNoDescription(message), message);
	}

	private static void show(Alert.AlertType type, String activity, String message) {
		ActivityLog.record(levelOf(type), FailureText.orNoDescription(activity));

		// Without a header of its own the band would only repeat the type of the icon, so the icon goes beside the text.
		Alert alert = alert(type, message);
		alert.setHeaderText(null);
		alert.getDialogPane().setPrefWidth(alert.getDialogPane().getPrefWidth() + ICON_BESIDE_THE_TEXT);
		alert.showAndWait();
	}

	private static Alert summary(Alert.AlertType type, String header, String text) {
		ActivityLog.record(levelOf(type), header + ": " + text);

		Alert alert = alert(type, text);
		alert.setHeaderText(header);
		return alert;
	}

	// The label of a plain Alert cuts a long message off without any way of reading the rest.
	private static Alert alert(Alert.AlertType type, String message, ButtonType... buttons) {
		String text = FailureText.orNoDescription(message);
		String shown = MessageLayout.preview(text);

		Alert alert = alertWith(previewOf(text), type, text, buttons);
		alert.getDialogPane().setPrefWidth(textWidthOf(shown) + AROUND_THE_TEXT);
		return alert;
	}

	private static Level levelOf(Alert.AlertType type) {
		return switch (type) {
			case ERROR -> Level.SEVERE;
			case WARNING -> Level.WARNING;
			default -> Level.INFO;
		};
	}

	// For what must be read before answering: the end of the text can hold the warning or the word to type.
	private static Alert wholeMessageAlert(Alert.AlertType type, String message, ButtonType... buttons) {
		Alert alert = alertWith(messageArea(message), type, message, buttons);
		alert.getDialogPane().setPrefWidth(MESSAGE_WIDTH);
		return alert;
	}

	private static Alert alertWith(Node content, Alert.AlertType type, String message, ButtonType... buttons) {
		Alert alert = new Alert(type, message, buttons);
		alert.getDialogPane().setContent(content);
		return alert;
	}

	public static void dispatchCompleted(String message) {
		Alert alert = summary(Alert.AlertType.INFORMATION, DISPATCH_COMPLETED, message);
		alert.setTitle(DISPATCH_COMPLETED);
		alert.setGraphic(greenCheck());
		alert.showAndWait();
	}

	private static Node greenCheck() {
		SVGPath check = new SVGPath();
		check.setContent(CHECK_SHAPE);
		check.setFill(Color.TRANSPARENT);
		check.setStroke(Color.WHITE);
		check.setStrokeWidth(3);

		return new StackPane(new Circle(CHECK_RADIUS, CHECK_GREEN), check);
	}

	private static Node previewOf(String message) {

		if (!MessageLayout.isLong(message)) {
			return fittedText(message);
		}

		Button seeAll = new Button("Ver tudo");
		seeAll.setFocusTraversable(false);
		seeAll.setOnAction(event -> showWholeMessage(message, seeAll.getScene().getWindow()));

		return new VBox(8, fittedText(MessageLayout.preview(message)), seeAll);
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

	// Takes exactly the room of its text, so a short message leaves no empty space, and it is never cut.
	private static Label fittedText(String message) {
		Label label = new Label(message);
		label.setWrapText(true);
		label.setPrefWidth(textWidthOf(message));
		label.setMinHeight(Region.USE_PREF_SIZE);
		label.setMaxHeight(Double.MAX_VALUE);
		return label;
	}

	private static double textWidthOf(String message) {
		return Math.clamp(new Text(message).getLayoutBounds().getWidth() + ROUNDING_OF_THE_TEXT, MIN_TEXT_WIDTH, MAX_TEXT_WIDTH);
	}

	public static void collectionSummary(CollectionResult result, Path archiveRoot, SourceMode sourceMode, String header) {

		summary(
				CollectionSummaryText.collectionHasProblems(result, sourceMode) ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				header,
				CollectionSummaryText.collection(result, archiveRoot, sourceMode)).showAndWait();
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

		summary(allApproved ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, "Solicitação concluída", VoidingText.summary(outcomes, environment)).showAndWait();
	}

	public static void readingSummary(CollectionResult result, SourceMode sourceMode) {
		boolean problems = !result.failures().isEmpty() || result.summary().errors() > 0;

		summary(problems ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION, "Leitura concluída", CollectionSummaryText.reading(result, sourceMode)).showAndWait();
	}

	public static void missingSearchSummary(CollectionResult result, List<XmlDocument.Invoice> found, Path archiveRoot) {

		summary(
				CollectionSummaryText.missingSearchHasProblems(result) ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
				"Busca de notas faltantes concluída",
				CollectionSummaryText.missingSearch(result, found, archiveRoot)).showAndWait();
	}
}