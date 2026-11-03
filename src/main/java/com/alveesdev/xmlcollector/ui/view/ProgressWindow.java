package com.alveesdev.xmlcollector.ui.view;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.ui.controller.ProgressController;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class ProgressWindow {

	private static final String PROGRESS_FXML = "/com/alveesdev/xmlcollector/view/CollectionProgress.fxml";

	private final Stage stage = new Stage();
	private final ProgressController controller;

	public ProgressWindow(Window owner, Map<Integer, String> sourceLabels) throws IOException {

		URL location = ProgressWindow.class.getResource(PROGRESS_FXML);

		if (location == null) {
			throw new IOException("Arquivo da tela de progresso não encontrado.");
		}

		FXMLLoader loader = new FXMLLoader(location);
		Parent root = loader.load();

		controller = loader.getController();
		controller.showSources(sourceLabels);

		stage.initStyle(StageStyle.UNDECORATED);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(owner);
		stage.setResizable(false);
		stage.setScene(new Scene(root));
	}

	public void setCancelAction(Runnable cancelAction) {
		controller.setCancelAction(cancelAction);
	}

	public void update(CollectionProgress progress) {
		controller.update(progress);
	}

	public void show() {
		stage.show();
	}

	public void close() {
		stage.close();
	}
}
