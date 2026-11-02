package com.alveesdev.xmlcollector.ui.controller;

import java.util.List;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.ui.model.MissingTableModel;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class MissingTabController {

	private static final double COLUMN_WIDTH = 110;

	private final SettingsService settingsService;

	private MissingTableModel model;

	@FXML
	private TableView<List<String>> missingTable;

	public MissingTabController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@FXML
	public void initialize() {
		refreshPdvColumns();
	}

	public void show(CollectionResult result) {
		show(MissingTableModel.fromResult(result));
	}

	public void refreshPdvColumns() {

		int pdvCount = settingsService.current().pdvCount();

		boolean keepCurrent = model != null
				&& (model.isSeriesBased() || model.getHeaders().size() == pdvCount);

		if (keepCurrent) {
			return;
		}

		show(MissingTableModel.forPdvs(pdvCount));
	}

	private void show(MissingTableModel newModel) {

		model = newModel;

		missingTable.getColumns().clear();

		for (int index = 0; index < newModel.getHeaders().size(); index++) {
			final int columnIndex = index;

			TableColumn<List<String>, String> column = new TableColumn<>(newModel.getHeaders().get(index));
			column.setPrefWidth(COLUMN_WIDTH);
			column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(columnIndex)));

			missingTable.getColumns().add(column);
		}

		missingTable.setItems(FXCollections.observableArrayList(newModel.getRows()));
	}
}
