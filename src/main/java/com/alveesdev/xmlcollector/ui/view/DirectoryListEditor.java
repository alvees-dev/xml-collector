package com.alveesdev.xmlcollector.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;

import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult;
import com.alveesdev.xmlcollector.application.connection.DirectoryTester;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.DirectoryTestText;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DirectoryListEditor {

	private static final double STATUS_MAX_WIDTH = 300;

	private record Row(TextField field, Button testButton, Label status) {
	}

	private final VBox listBox;
	private final ScrollPane scrollPane;
	private final String labelPrefix;
	private final double labelWidth;
	private final double fieldWidth;
	private final DirectoryTester tester;
	private final Function<String, DirectoryTarget> targets;

	private final List<Row> rows = new ArrayList<>();

	public DirectoryListEditor(VBox listBox, ScrollPane scrollPane, String labelPrefix, double labelWidth,
			double fieldWidth, DirectoryTester tester, Function<String, DirectoryTarget> targets) {
		this.listBox = listBox;
		this.scrollPane = scrollPane;
		this.labelPrefix = labelPrefix;
		this.labelWidth = labelWidth;
		this.fieldWidth = fieldWidth;
		this.tester = tester;
		this.targets = targets;
	}

	public void load(List<String> savedDirectories) {
		if (savedDirectories.isEmpty()) {
			addRow("");
		} else {
			savedDirectories.forEach(this::addRow);
		}
	}

	public void addRow(String directory) {
		int number = rows.size() + 1;

		Label label = new Label(String.format("%s %02d", labelPrefix, number));
		label.setMinWidth(labelWidth);

		TextField field = new TextField(directory);
		field.setPrefWidth(fieldWidth);

		Button testButton = new Button("Teste");

		HBox line = new HBox(8, label, field, testButton);
		line.setAlignment(Pos.CENTER_LEFT);

		Label status = new Label();
		status.setWrapText(true);
		status.setMaxWidth(STATUS_MAX_WIDTH);
		status.setVisible(false);
		status.managedProperty().bind(status.visibleProperty());
		VBox.setMargin(status, new Insets(2, 0, 0, labelWidth + 8));

		VBox rowBox = new VBox(line, status);
		rowBox.setPadding(new Insets(8, 0, 0, 15));

		Row row = new Row(field, testButton, status);
		testButton.setOnAction(event -> test(row));

		rows.add(row);
		listBox.getChildren().add(rowBox);

		Platform.runLater(() -> scrollPane.setVvalue(1.0));
	}

	public void removeLastRow() {
		if (rows.size() <= 1) {
			return;
		}

		int lastIndex = rows.size() - 1;
		rows.remove(lastIndex);
		listBox.getChildren().remove(lastIndex);
	}

	public List<String> values() {
		List<String> values = new ArrayList<>();

		for (Row row : rows) {
			values.add(row.field().getText().trim());
		}

		return values;
	}

	public void testAll() {
		rows.forEach(this::test);
	}

	private void test(Row row) {

		DirectoryTarget target = targets.apply(row.field().getText().trim());

		row.testButton().setDisable(true);
		showStatus(row, DirectoryTestText.RUNNING, List.of());

		Thread thread = new Thread(() -> {
			DirectoryTestResult result = tester.test(target);

			Platform.runLater(() -> {
				row.testButton().setDisable(false);
				showStatus(row, DirectoryTestText.of(result), List.of(styleOf(result)));
				recordTest(target, result);
			});
		}, "directory-test-row");

		thread.setDaemon(true);
		thread.start();
	}

	private void recordTest(DirectoryTarget target, DirectoryTestResult result) {
		ActivityLog.record(result.status() == DirectoryTestResult.Status.OK ? Level.INFO : Level.WARNING,
				"Testar diretório " + labelPrefix + " " + target.directory() + ": " + DirectoryTestText.of(result));
	}

	private static void showStatus(Row row, String text, List<String> styles) {
		row.status().setText(text);
		row.status().getStyleClass().setAll("label");
		row.status().getStyleClass().addAll(styles);
		row.status().setVisible(true);
	}

	public static String styleOf(DirectoryTestResult result) {
		return switch (result.status()) {
			case OK -> "test-ok";
			case WARNING -> "test-warning";
			case FAILED -> "text-danger";
		};
	}
}
