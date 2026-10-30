package com.alveesdev.xmlcollector.ui.view;

import java.io.IOException;
import java.net.URL;

import com.alveesdev.xmlcollector.ui.controller.ProgressController;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class ProgressWindow {

	private static final String PROGRESS_FXML = "/com/alveesdev/xmlcollector/view/CollectingXmlProgressBarV0.1.fxml";

	private final Stage stage = new Stage();

	public ProgressWindow(Window owner, ReadOnlyDoubleProperty progress, ReadOnlyStringProperty title,
			ReadOnlyStringProperty message, Runnable onCancel) throws IOException {

		URL location = ProgressWindow.class.getResource(PROGRESS_FXML);

		if (location == null) {
			throw new IOException("Arquivo da tela de progresso não encontrado.");
		}

		FXMLLoader loader = new FXMLLoader(location);
		Parent root = loader.load();

		ProgressController controller = loader.getController();
		controller.bind(progress, title, message, onCancel);

		stage.initStyle(StageStyle.UNDECORATED);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(owner);
		stage.setResizable(false);
		stage.setScene(new Scene(root));
	}

	public void show() {
		stage.show();
	}

	public void close() {
		stage.close();
	}
}