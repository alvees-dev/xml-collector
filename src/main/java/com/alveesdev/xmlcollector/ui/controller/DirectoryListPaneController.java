package com.alveesdev.xmlcollector.ui.controller;

import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;

import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult;
import com.alveesdev.xmlcollector.application.connection.DirectoryTester;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.DirectoryTestText;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.view.DirectoryListEditor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class DirectoryListPaneController {

	private static final double ROW_LABEL_WIDTH = 45;
	private static final double FIELD_WIDTH = 219;

	private final DirectoryTester directoryTester;

	@FXML
	private VBox serverBox;

	@FXML
	private TextField txtServer;

	@FXML
	private Button btnTestServer;

	@FXML
	private Label lblServerStatus;

	@FXML
	private Label lblTitle;

	@FXML
	private ScrollPane scrollPane;

	@FXML
	private VBox listBox;

	private DirectoryListEditor editor;

	public DirectoryListPaneController(DirectoryTester directoryTester) {
		this.directoryTester = directoryTester;
	}

	void load(String title, String rowPrefix, Function<String, DirectoryTarget> targets, List<String> directories) {
		lblTitle.setText(title);
		editor = new DirectoryListEditor(listBox, scrollPane, rowPrefix, ROW_LABEL_WIDTH, FIELD_WIDTH, directoryTester, targets);
		editor.load(directories);
	}

	List<String> directories() {
		return editor.values();
	}

	@FXML
	private void onAdd() {
		editor.addRow("");
	}

	@FXML
	private void onRemove() {
		editor.removeLastRow();
	}

	@FXML
	private void onTestAll() {
		editor.testAll();
	}

	// Only the list of the PDV has a server, where the registers upload their files by themselves.
	void showServer(String directory) {
		txtServer.setText(directory);
		serverBox.setManaged(true);
		serverBox.setVisible(true);
	}

	String serverDirectory() {
		return txtServer.getText().trim();
	}

	@FXML
	private void onTestServer() {

		DirectoryTarget target = new DirectoryTarget(serverDirectory(), List.of(), null);

		btnTestServer.setDisable(true);
		showServerStatus(DirectoryTestText.RUNNING, List.of());

		TaskRunner.inBackground("server-test", () -> directoryTester.test(target),
				result -> {
					btnTestServer.setDisable(false);
					showServerStatus(DirectoryTestText.of(result), List.of(DirectoryListEditor.styleOf(result)));
					ActivityLog.record(result.status() == DirectoryTestResult.Status.OK ? Level.INFO : Level.WARNING,
							"Testar diretório Servidor " + target.directory() + ": " + DirectoryTestText.of(result));
				},
				error -> {
					btnTestServer.setDisable(false);
					showServerStatus(FailureText.of(error), List.of("text-danger"));
				});
	}

	private void showServerStatus(String text, List<String> styles) {
		lblServerStatus.setText(text);
		lblServerStatus.getStyleClass().setAll("label");
		lblServerStatus.getStyleClass().addAll(styles);
		// Only takes room once there is a result, so the field alone stays in the middle between the two lines.
		lblServerStatus.setManaged(true);
		lblServerStatus.setVisible(true);
	}
}
