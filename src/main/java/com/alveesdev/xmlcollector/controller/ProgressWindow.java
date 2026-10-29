package com.alveesdev.xmlcollector.controller;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class ProgressWindow {

	private final Stage stage = new Stage();

	public ProgressWindow(Window owner, ReadOnlyDoubleProperty progress, ReadOnlyStringProperty title,
			ReadOnlyStringProperty message, Runnable onCancel) {

		Label heading = new Label("Coletando XML");
		heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		Label source = new Label();
		source.textProperty().bind(title);

		ProgressBar bar = new ProgressBar();
		bar.setPrefWidth(380);
		bar.progressProperty().bind(progress);

		Label counts = new Label();
		counts.textProperty().bind(message);

		Button cancel = new Button("Cancelar");
		cancel.setOnAction(event -> {
			cancel.setDisable(true);
			cancel.setText("Cancelando...");
			onCancel.run();
		});

		VBox root = new VBox(10, heading, source, bar, counts, cancel);
		root.setAlignment(Pos.CENTER_LEFT);
		root.setPadding(new Insets(20));
		root.setStyle("-fx-background-color: white; -fx-border-color: #888888; -fx-border-width: 1;");

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