package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.alveesdev.xmlcollector.application.CollectionRequest;
import com.alveesdev.xmlcollector.application.CollectionResult;
import com.alveesdev.xmlcollector.application.CollectionService;
import com.alveesdev.xmlcollector.config.AppPaths;
import com.alveesdev.xmlcollector.config.AppSettings;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.ui.model.MissingTableModel;
import com.alveesdev.xmlcollector.ui.model.NfceRow;
import com.alveesdev.xmlcollector.ui.model.NfceRowMapper;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;
import com.alveesdev.xmlcollector.ui.view.SettingsWindow;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class MainScreenController {

	private static final double MISSING_COLUMN_WIDTH = 110;

	private final CollectionService collectionService = new CollectionService();
	private final AppPaths paths = AppPaths.fromJarLocation();

	private MissingTableModel missingModel;

	@FXML
	private RadioButton RadioButtonColetaXMLCaminhoPDV;

	@FXML
	private TextField txtDirectory;

	@FXML
	private Button btnVerificarDir;

	@FXML
	private DatePicker dpFrom;

	@FXML
	private DatePicker dpTo;

	@FXML
	private TableView<NfceRow> tableView;

	@FXML
	private TableColumn<NfceRow, String> numberColumn;

	@FXML
	private TableColumn<NfceRow, String> serieColumn;

	@FXML
	private TableColumn<NfceRow, String> totalValueColumn;

	@FXML
	private TableColumn<NfceRow, String> emissionDateColumn;

	@FXML
	private TableColumn<NfceRow, String> nfKeyColumn;

	@FXML
	private TableColumn<NfceRow, String> statusColumn;

	@FXML
	private TableView<List<String>> missingTableView;

	@FXML
	public void initialize() {
		numberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));

		serieColumn.setCellValueFactory(new PropertyValueFactory<>("series"));

		totalValueColumn.setCellValueFactory(new PropertyValueFactory<>("totalValue"));

		emissionDateColumn.setCellValueFactory(new PropertyValueFactory<>("emissionDate"));

		nfKeyColumn.setCellValueFactory(new PropertyValueFactory<>("accessKey"));

		statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

		RadioButtonColetaXMLCaminhoPDV.setSelected(true);

		txtDirectory.disableProperty().bind(RadioButtonColetaXMLCaminhoPDV.selectedProperty());

		LocalDate today = LocalDate.now();
		dpFrom.setValue(today.withDayOfMonth(1));
		dpTo.setValue(today.withDayOfMonth(today.lengthOfMonth()));

		refreshMissingTable();
	}

	@FXML
	private void onOpenSettings() {
		SettingsWindow.showAndWait(tableView.getScene().getWindow());
		refreshMissingTable();
	}

	@FXML
	private void onVerificarDiretorio() {

		Optional<CollectionRequest> request = buildRequest();

		if (request.isEmpty()) {
			return;
		}

		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		CollectionTask task = new CollectionTask(collectionService, request.get(), pdvMode);

		ProgressWindow progressWindow;

		try {
			progressWindow = new ProgressWindow(
					tableView.getScene().getWindow(),
					task.progressProperty(),
					task.titleProperty(),
					task.messageProperty(),
					task::cancel);
		} catch (IOException e) {
			Dialogs.error("Erro ao abrir a tela de progresso: " + e.getMessage());
			return;
		}

		btnVerificarDir.setDisable(true);

		task.setOnSucceeded(event -> {
			progressWindow.close();
			btnVerificarDir.setDisable(false);
			showResult(task.getValue(), pdvMode);
		});

		task.setOnFailed(event -> {
			progressWindow.close();
			btnVerificarDir.setDisable(false);
			Dialogs.error("Erro ao processar o diretório: " + task.getException().getMessage());
		});

		task.setOnCancelled(event -> {
			progressWindow.close();
			btnVerificarDir.setDisable(false);
			Dialogs.warning("Coleta cancelada. Os arquivos já copiados foram mantidos.");
		});

		progressWindow.show();

		Thread collectThread = new Thread(task);
		collectThread.setDaemon(true);
		collectThread.start();
	}

	private Optional<CollectionRequest> buildRequest() {

		LocalDate from = dpFrom.getValue();
		LocalDate to = dpTo.getValue();

		if (from == null || to == null || from.isAfter(to)) {
			Dialogs.warning("Informe um período válido (De / Até).");
			return Optional.empty();
		}

		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		if (!pdvMode && txtDirectory.getText().isBlank()) {
			Dialogs.warning("Informe o diretório a ser verificado.");
			return Optional.empty();
		}

		AppSettings settings = AppSettings.getInstance();

		List<String> directories = pdvMode
				? settings.getPdvDirectories()
				: List.of(txtDirectory.getText().trim());

		return Optional.of(new CollectionRequest(
				directories, new DateRange(from, to), settings.getOrganizeMode(), paths.xmlArchiveDir()));
	}

	private void showResult(CollectionResult result, boolean pdvMode) {

		List<NfceRow> rows = result.entries().stream()
				.map(NfceRowMapper::toRow)
				.toList();

		tableView.setItems(FXCollections.observableArrayList(rows));

		showMissing(MissingTableModel.fromResult(result, !pdvMode));

		Dialogs.collectionSummary(result, paths.xmlArchiveDir(), pdvMode);
	}

	private void showMissing(MissingTableModel model) {

		missingModel = model;

		missingTableView.getColumns().clear();

		for (int index = 0; index < model.getHeaders().size(); index++) {
			final int columnIndex = index;

			TableColumn<List<String>, String> column = new TableColumn<>(model.getHeaders().get(index));
			column.setPrefWidth(MISSING_COLUMN_WIDTH);
			column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(columnIndex)));

			missingTableView.getColumns().add(column);
		}

		missingTableView.setItems(FXCollections.observableArrayList(model.getRows()));
	}

	private void refreshMissingTable() {

		int pdvCount = AppSettings.getInstance().getPdvCount();

		boolean keepCurrent = missingModel != null
				&& (missingModel.isSeriesBased() || missingModel.getHeaders().size() == pdvCount);

		if (keepCurrent) {
			return;
		}

		showMissing(MissingTableModel.forPdvs(pdvCount));
	}
}