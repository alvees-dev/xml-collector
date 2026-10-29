package com.alveesdev.xmlcollector.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.alveesdev.xmlcollector.config.AppPaths;
import com.alveesdev.xmlcollector.config.AppSettings;
import com.alveesdev.xmlcollector.model.MissingTableModel;
import com.alveesdev.xmlcollector.model.NfceEntry;
import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.service.CollectionProgress;
import com.alveesdev.xmlcollector.service.CollectionRequest;
import com.alveesdev.xmlcollector.service.CollectionResult;
import com.alveesdev.xmlcollector.service.CollectionService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
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

		CollectionRequest collectionRequest = request.get();

		btnVerificarDir.setDisable(true);

		Task<CollectionResult> task = new Task<>() {

			@Override
			protected CollectionResult call() {
				return collectionService.collect(collectionRequest, this::publishProgress, this::isCancelled);
			}

			private void publishProgress(CollectionProgress progress) {
				updateTitle(progress.sourceLabel());

				if (progress.isCounting()) {
					updateProgress(-1, -1);
					updateMessage("Contando arquivos...");
					return;
				}

				updateProgress(progress.processed(), Math.max(progress.total(), 1));
				updateMessage(String.format("%d de %d arquivos", progress.processed(), progress.total()));
			}
		};

		ProgressWindow progressWindow = new ProgressWindow(
				tableView.getScene().getWindow(),
				task.progressProperty(),
				task.titleProperty(),
				task.messageProperty(),
				() -> task.cancel());

		task.setOnSucceeded(event -> {
			progressWindow.close();
			btnVerificarDir.setDisable(false);
			showResult(task.getValue());
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
				directories, from, to, settings.getOrganizeMode(), paths.xmlArchiveDir(), !pdvMode));
	}

	private void showResult(CollectionResult result) {

		List<NfceRow> rows = result.invoices().stream()
				.map(MainScreenController::toRow)
				.toList();

		tableView.setItems(FXCollections.observableArrayList(rows));

		showMissing(MissingTableModel.fromColumns(result.missingColumns(), result.groupedBySeries()));

		Dialogs.collectionSummary(result, paths.xmlArchiveDir());
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

	private static NfceRow toRow(NfceEntry entry) {
		return new NfceRow(entry.number(), entry.series(), entry.totalValue(),
				entry.emissionDate(), entry.accessKey(), entry.status());
	}
}