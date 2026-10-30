package com.alveesdev.xmlcollector.ui.controller;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class ProgressController {

	@FXML
	private Label lblSource;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private Label lblCounts;

	@FXML
	private Button btnCancel;

	private Runnable cancelAction = () -> {
	};

	public void bind(ReadOnlyDoubleProperty progress, ReadOnlyStringProperty source,
			ReadOnlyStringProperty counts, Runnable cancelAction) {

		progressBar.progressProperty().bind(progress);
		lblSource.textProperty().bind(source);
		lblCounts.textProperty().bind(counts);
		this.cancelAction = cancelAction;
	}

	@FXML
	private void onCancel() {
		btnCancel.setDisable(true);
		btnCancel.setText("Cancelando...");
		cancelAction.run();
	}
}