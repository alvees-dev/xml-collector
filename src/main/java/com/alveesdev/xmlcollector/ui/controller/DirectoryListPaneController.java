package com.alveesdev.xmlcollector.ui.controller;

import java.util.List;
import java.util.function.Function;

import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTester;
import com.alveesdev.xmlcollector.ui.view.DirectoryListEditor;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

public class DirectoryListPaneController {

	private static final double ROW_LABEL_WIDTH = 45;
	private static final double FIELD_WIDTH = 219;

	private final DirectoryTester directoryTester;

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
}
