package com.alveesdev.xmlcollector.view;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class SettingsController {

	@FXML
	private Button btnSave;

	@FXML
	private Button btnCancel;

	@FXML
	private void onSave() {
		closeWindow();
	}

	@FXML
	private void onCancel() {
		closeWindow();
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}