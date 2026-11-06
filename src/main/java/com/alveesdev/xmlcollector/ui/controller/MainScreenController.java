package com.alveesdev.xmlcollector.ui.controller;

import com.alveesdev.xmlcollector.application.settings.Theme;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.view.PersonalizationWindow;
import com.alveesdev.xmlcollector.ui.view.SettingsWindow;
import com.alveesdev.xmlcollector.ui.view.Themes;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class MainScreenController {

	private static final String DIRECTORIES_TAB = "Diretórios";

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
		collectionTabController.setOnCollected((request, result) -> missingTabController.show(request, result));
		missingTabController.setOnFound(collectionTabController::addFound);
		collectionTabController.setOnOpenDirectories(() -> openSettings(DIRECTORIES_TAB));
	}

	@FXML
	private void onOpenSettings() {
		openSettings(null);
	}

	private void openSettings(String tabName) {
		ActivityLog.record(tabName == null ? "Abriu configurações" : "Abriu configurações, aba " + tabName);
		settingsWindow.showAndWait(root.getScene().getWindow(), tabName);
		missingTabController.refreshPdvColumns();
		collectionTabController.refreshServerDirectory();
	}

	@FXML
	private void onOpenPersonalization() {
		ActivityLog.record("Abriu personalização");
		PersonalizationWindow.showAndWait(root.getScene().getWindow());
	}

	@FXML
	private void onToggleTheme() {
		Theme theme = Themes.current().toggled();
		ActivityLog.record("Tema alterado para " + theme.label());
		Themes.use(theme);
	}

	@FXML
	private void onExit() {
		Platform.exit();
	}
}
