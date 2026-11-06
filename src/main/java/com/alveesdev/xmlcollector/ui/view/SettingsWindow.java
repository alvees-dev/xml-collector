package com.alveesdev.xmlcollector.ui.view;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;

public final class SettingsWindow {

	private static final String SETTINGS_FXML = "/com/alveesdev/xmlcollector/view/Settings.fxml";

	private final Callback<Class<?>, Object> controllerFactory;

	public SettingsWindow(Callback<Class<?>, Object> controllerFactory) {
		this.controllerFactory = controllerFactory;
	}

	public void showAndWait(Window owner) {
		showAndWait(owner, null);
	}

	// With the tab already open, for a shortcut that leads to one part of the settings.
	public void showAndWait(Window owner, String tabName) {
		try {
			URL fxmlLocation = SettingsWindow.class.getResource(SETTINGS_FXML);

			if (fxmlLocation == null) {
				Dialogs.error("Não foi possível abrir as configurações.");
				return;
			}

			FXMLLoader loader = new FXMLLoader(fxmlLocation);
			loader.setControllerFactory(controllerFactory);

			Parent root = loader.load();
			Scene scene = new Scene(root);

			if (tabName != null && root.lookup(".tab-pane") instanceof TabPane tabs) {
				tabs.getTabs().stream()
						.filter(tab -> tabName.equals(tab.getText()))
						.findFirst()
						.ifPresent(tab -> tabs.getSelectionModel().select(tab));
			}

			Stage stage = new Stage();
			stage.setTitle("Configurações");
			stage.setScene(scene);
			stage.setResizable(false);
			stage.initModality(Modality.APPLICATION_MODAL);
			stage.initOwner(owner);
			stage.showAndWait();

		} catch (IOException e) {
			Dialogs.error("Erro ao abrir as configurações: " + e.getMessage());
		}
	}
}
