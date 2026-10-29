package com.alveesdev.xmlcollector.ui.controller;

import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.config.AppSettings;
import com.alveesdev.xmlcollector.config.OrganizeMode;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SettingsController {

	@FXML
	private Label lblTitle;

	@FXML
	private Button btnSave;

	@FXML
	private Button btnCancel;

	@FXML
	private ScrollPane pdvScrollPane;

	@FXML
	private VBox pdvListBox;

	@FXML
	private RadioButton radioSingleFolder;

	@FXML
	private RadioButton radioByDay;

	private final List<TextField> pdvFields = new ArrayList<>();

	private double dragOffsetX;
	private double dragOffsetY;

	@FXML
	public void initialize() {
		List<String> savedDirectories = AppSettings.getInstance().getPdvDirectories();

		if (savedDirectories.isEmpty()) {
			addPdvRow("");
		} else {
			savedDirectories.forEach(this::addPdvRow);
		}

		if (AppSettings.getInstance().getOrganizeMode() == OrganizeMode.BY_DAY) {
			radioByDay.setSelected(true);
		} else {
			radioSingleFolder.setSelected(true);
		}
	}

	@FXML
	private void onAddPdv() {
		addPdvRow("");
	}

	@FXML
	private void onRemovePdv() {
		if (pdvFields.size() <= 1) {
			return;
		}

		int lastIndex = pdvFields.size() - 1;
		pdvFields.remove(lastIndex);
		pdvListBox.getChildren().remove(lastIndex);
	}

	@FXML
	private void onSave() {
		List<String> directories = new ArrayList<>();
		for (TextField field : pdvFields) {
			directories.add(field.getText().trim());
		}

		AppSettings settings = AppSettings.getInstance();
		settings.setPdvDirectories(directories);
		settings.setOrganizeMode(radioByDay.isSelected() ? OrganizeMode.BY_DAY : OrganizeMode.SINGLE_FOLDER);

		closeWindow();
	}

	@FXML
	private void onCancel() {
		closeWindow();
	}

	@FXML
	private void onTitlePressed(MouseEvent event) {
		dragOffsetX = event.getSceneX();
		dragOffsetY = event.getSceneY();
	}

	@FXML
	private void onTitleDragged(MouseEvent event) {
		Stage stage = (Stage) lblTitle.getScene().getWindow();
		stage.setX(event.getScreenX() - dragOffsetX);
		stage.setY(event.getScreenY() - dragOffsetY);
	}

	private void addPdvRow(String directory) {
		int number = pdvFields.size() + 1;

		Label label = new Label(String.format("PDV %02d", number));
		label.setMinWidth(45);

		TextField field = new TextField(directory);
		field.setPrefWidth(219);

		Button testButton = new Button("Teste");

		HBox row = new HBox(8, label, field, testButton);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(8, 0, 0, 15));

		pdvFields.add(field);
		pdvListBox.getChildren().add(row);

		Platform.runLater(() -> pdvScrollPane.setVvalue(1.0));
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}