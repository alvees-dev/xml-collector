package com.alveesdev.xmlcollector.ui.controller;

import com.alveesdev.xmlcollector.ui.view.SettingsWindow;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class MainScreenController {

	private final SettingsWindow settingsWindow;

	@FXML
	private VBox root;

	@FXML
	private CollectionTabController collectionTabController;

	@FXML
	private MissingTabController missingTabController;

	public MainScreenController(SettingsWindow settingsWindow) {
		this.settingsWindow = settingsWindow;
	}

	@FXML
	public void initialize() {
		collectionTabController.setOnCollected(missingTabController::show);
	}

	@FXML
	private void onOpenSettings() {
		settingsWindow.showAndWait(root.getScene().getWindow());
		missingTabController.refreshPdvColumns();
	}
}
