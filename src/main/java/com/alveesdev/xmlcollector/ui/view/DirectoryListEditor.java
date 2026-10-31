package com.alveesdev.xmlcollector.ui.view;

import java.util.ArrayList;
import java.util.List;

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

	private final VBox listBox;
	private final ScrollPane scrollPane;
	private final String labelPrefix;
	private final double labelWidth;
	private final double fieldWidth;

	private final List<TextField> fields = new ArrayList<>();

	public DirectoryListEditor(VBox listBox, ScrollPane scrollPane, String labelPrefix, double labelWidth,
			double fieldWidth) {
		this.listBox = listBox;
		this.scrollPane = scrollPane;
		this.labelPrefix = labelPrefix;
		this.labelWidth = labelWidth;
		this.fieldWidth = fieldWidth;
	}

	public void load(List<String> savedDirectories) {
		if (savedDirectories.isEmpty()) {
			addRow("");
		} else {
			savedDirectories.forEach(this::addRow);
		}
	}

	public void addRow(String directory) {
		int number = fields.size() + 1;

		Label label = new Label(String.format("%s %02d", labelPrefix, number));
		label.setMinWidth(labelWidth);

		TextField field = new TextField(directory);
		field.setPrefWidth(fieldWidth);

		Button testButton = new Button("Teste");

		HBox row = new HBox(8, label, field, testButton);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(8, 0, 0, 15));

		fields.add(field);
		listBox.getChildren().add(row);

		Platform.runLater(() -> scrollPane.setVvalue(1.0));
	}

	public void removeLastRow() {
		if (fields.size() <= 1) {
			return;
		}

		int lastIndex = fields.size() - 1;
		fields.remove(lastIndex);
		listBox.getChildren().remove(lastIndex);
	}

	public List<String> values() {
		List<String> values = new ArrayList<>();

		for (TextField field : fields) {
			values.add(field.getText().trim());
		}

		return values;
	}
}