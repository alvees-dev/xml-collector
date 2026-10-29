package com.alveesdev.xmlcollector.controller;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class SettingsWindow {

	private static final String SETTINGS_FXML = "/com/alveesdev/xmlcollector/view/SettingsV0.1.fxml";

	private SettingsWindow() {
	}

	public static void showAndWait(Window owner) {
		try {
			URL fxmlLocation = SettingsWindow.class.getResource(SETTINGS_FXML);

			if (fxmlLocation == null) {
				Dialogs.error("Arquivo da tela de configurações não encontrado.");
				return;
			}

			Scene scene = new Scene(new FXMLLoader(fxmlLocation).load());

			Stage stage = new Stage();
			stage.initStyle(StageStyle.UNDECORATED);
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