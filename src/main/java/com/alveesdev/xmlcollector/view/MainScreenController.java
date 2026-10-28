package com.alveesdev.xmlcollector.view;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.AppPaths;
import com.alveesdev.xmlcollector.model.AppSettings;
import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.model.OrganizeMode;
import com.alveesdev.xmlcollector.xmlconfig.MissingNumberFinder;
import com.alveesdev.xmlcollector.xmlconfig.NfceFileData;
import com.alveesdev.xmlcollector.xmlconfig.XmlOrganizer;
import com.alveesdev.xmlcollector.xmlconfig.XmlProcessor;
import com.alveesdev.xmlcollector.xmlconfig.XmlRowReader;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainScreenController {

	private static final String SETTINGS_FXML = "/com/alveesdev/xmlcollector/view/SettingsV0.1.fxml";

	private static final double MISSING_COLUMN_WIDTH = 110;

	private record CollectionResult(
			ObservableList<NfceRow> lines,
			List<String> missingHeaders,
			ObservableList<ObservableList<String>> missingRows,
			List<String> failures) {
	}

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
	private Button btnCopiarXml;

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
	private TableView<ObservableList<String>> missingTableView;

	private boolean missingShowsSeries = false;

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

		refreshMissingColumns();
	}

	private static List<String> pdvHeaders(int pdvCount) {
		List<String> headers = new ArrayList<>();
		for (int number = 1; number <= pdvCount; number++) {
			headers.add(String.format("PDV%02d", number));
		}
		return headers;
	}

	private List<String> resolveDirectories(boolean pdvMode) {
		return pdvMode
				? AppSettings.getInstance().getPdvDirectories()
				: List.of(txtDirectory.getText());
	}

	private void showMissingColumns(List<String> headers) {
		missingTableView.getColumns().clear();
		missingTableView.getItems().clear();

		for (int index = 0; index < headers.size(); index++) {
			final int columnIndex = index;

			TableColumn<ObservableList<String>, String> column = new TableColumn<>(headers.get(index));
			column.setPrefWidth(MISSING_COLUMN_WIDTH);
			column.setCellValueFactory(cell -> {
				ObservableList<String> row = cell.getValue();
				return new SimpleStringProperty(columnIndex < row.size() ? row.get(columnIndex) : "");
			});

			missingTableView.getColumns().add(column);
		}
	}

	private void refreshMissingColumns() {
		if (missingShowsSeries) {
			return;
		}

		int pdvCount = AppSettings.getInstance().getPdvCount();

		if (missingTableView.getColumns().size() == pdvCount) {
			return;
		}

		showMissingColumns(pdvHeaders(pdvCount));
	}

	@FXML
	private void onOpenSettings() {
		try {
			URL fxmlLocation = getClass().getResource(SETTINGS_FXML);

			if (fxmlLocation == null) {
				new Alert(Alert.AlertType.ERROR, "Arquivo da tela de configurações não encontrado.").showAndWait();
				return;
			}

			Scene scene = new Scene(new FXMLLoader(fxmlLocation).load());

			Stage settingsStage = new Stage();
			settingsStage.initStyle(StageStyle.UNDECORATED);
			settingsStage.setTitle("Configurações");
			settingsStage.setScene(scene);
			settingsStage.setResizable(false);

			settingsStage.initModality(Modality.APPLICATION_MODAL);
			settingsStage.initOwner(tableView.getScene().getWindow());

			settingsStage.showAndWait();

			refreshMissingColumns();

		} catch (IOException e) {
			new Alert(Alert.AlertType.ERROR, "Erro ao abrir as configurações: " + e.getMessage()).showAndWait();
		}
	}

	@FXML
	private void onVerificarDiretorio() {

		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		if (!pdvMode && txtDirectory.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, "Informe o diretório a ser verificado.").showAndWait();
			return;
		}

		List<String> directories = resolveDirectories(pdvMode);

		btnVerificarDir.setDisable(true);

		Task<CollectionResult> task = new Task<>() {

			@Override
			protected CollectionResult call() throws Exception {

				ObservableList<NfceRow> lines = FXCollections.observableArrayList();
				List<String> failures = new ArrayList<>();

				if (!pdvMode) {
					Map<String, NfceFileData> allData =
							XmlProcessor.collectXml(directories.get(0), XmlRowReader::read);

					lines.addAll(buildRows(allData));

					List<String> seriesHeaders = new ArrayList<>();
					List<List<String>> missingBySeries = new ArrayList<>();

					MissingNumberFinder.findMissingBySeries(allData).forEach((series, ranges) -> {
						seriesHeaders.add("SERIE " + series);
						missingBySeries.add(ranges);
					});

					return new CollectionResult(lines, seriesHeaders, buildMissingRows(missingBySeries), failures);
				}

				List<List<String>> missingByPdv = new ArrayList<>();

				for (int i = 0; i < directories.size(); i++) {
					String pdvLabel = String.format("PDV %02d", i + 1);
					String pdvDirectory = directories.get(i);

					if (pdvDirectory.isBlank()) {
						failures.add(pdvLabel + ": diretório não informado");
						missingByPdv.add(List.of());
						continue;
					}

					try {
						Map<String, NfceFileData> allData =
								XmlProcessor.collectXml(pdvDirectory, XmlRowReader::read);

						lines.addAll(buildRows(allData));
						missingByPdv.add(MissingNumberFinder.findMissing(allData));

					} catch (Exception e) {
						failures.add(pdvLabel + ": diretório inacessível (" + pdvDirectory + ")");
						missingByPdv.add(List.of());
					}
				}

				return new CollectionResult(lines, pdvHeaders(directories.size()),
						buildMissingRows(missingByPdv), failures);
			}
		};

		task.setOnSucceeded(event -> {
			CollectionResult result = task.getValue();

			tableView.setItems(result.lines());

			missingShowsSeries = !pdvMode;
			showMissingColumns(result.missingHeaders());
			missingTableView.setItems(result.missingRows());

			btnVerificarDir.setDisable(false);

			if (!result.failures().isEmpty()) {
				new Alert(Alert.AlertType.WARNING,
						"Não foi possível ler os seguintes PDVs:\n\n" + String.join("\n", result.failures()))
						.showAndWait();
			}
		});

		task.setOnFailed(event -> {
			Throwable error = task.getException();
			new Alert(Alert.AlertType.ERROR, "Erro ao processar o diretório: " + error.getMessage()).showAndWait();
			btnVerificarDir.setDisable(false);
		});

		Thread collectXmlThread = new Thread(task);
		collectXmlThread.setDaemon(true);
		collectXmlThread.start();
	}

	@FXML
	private void onCopiarXml() {

		LocalDate from = dpFrom.getValue();
		LocalDate to = dpTo.getValue();

		if (from == null || to == null || from.isAfter(to)) {
			new Alert(Alert.AlertType.WARNING, "Informe um período válido (De / Até).").showAndWait();
			return;
		}

		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		if (!pdvMode && txtDirectory.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, "Informe o diretório a ser copiado.").showAndWait();
			return;
		}

		List<String> directories = resolveDirectories(pdvMode);
		OrganizeMode mode = AppSettings.getInstance().getOrganizeMode();
		Path archiveRoot = AppPaths.fromJarLocation().xmlArchiveDir();

		btnCopiarXml.setDisable(true);

		Task<XmlOrganizer.CopyReport> task = new Task<>() {

			@Override
			protected XmlOrganizer.CopyReport call() {

				XmlOrganizer.CopyReport report = new XmlOrganizer.CopyReport();

				for (int i = 0; i < directories.size(); i++) {
					String label = pdvMode ? String.format("PDV %02d", i + 1) : "Diretório";
					String directory = directories.get(i);

					if (directory.isBlank()) {
						report.addFailure(label + ": diretório não informado");
						continue;
					}

					try {
						XmlOrganizer.copyDirectory(Path.of(directory), from, to, archiveRoot, mode, report);
					} catch (Exception e) {
						report.addFailure(label + ": diretório inacessível (" + directory + ")");
					}
				}

				return report;
			}
		};

		task.setOnSucceeded(event -> {
			XmlOrganizer.CopyReport report = task.getValue();
			btnCopiarXml.setDisable(false);

			StringBuilder message = new StringBuilder();
			message.append("Copiados: ").append(report.getCopied());
			message.append("\nJá existiam no destino: ").append(report.getAlreadyExisting());
			message.append("\nFora do período: ").append(report.getOutOfPeriod());
			message.append("\nSem data ou ilegíveis: ").append(report.getUnreadable());
			message.append("\n\nDestino: ").append(archiveRoot);

			if (!report.getFailures().isEmpty()) {
				message.append("\n\nFalhas:\n").append(String.join("\n", report.getFailures()));
			}

			Alert.AlertType type = report.getFailures().isEmpty()
					? Alert.AlertType.INFORMATION
					: Alert.AlertType.WARNING;

			new Alert(type, message.toString()).showAndWait();
		});

		task.setOnFailed(event -> {
			Throwable error = task.getException();
			new Alert(Alert.AlertType.ERROR, "Erro ao copiar os XMLs: " + error.getMessage()).showAndWait();
			btnCopiarXml.setDisable(false);
		});

		Thread copyThread = new Thread(task);
		copyThread.setDaemon(true);
		copyThread.start();
	}

	private static List<NfceRow> buildRows(Map<String, NfceFileData> allData) {

		Set<String> cancelEventFiles = new HashSet<>();
		Set<String> cancelledKeys = new HashSet<>();
		allData.forEach((file, data) -> {
			if (data != null && data.cancelledKey() != null) {
				cancelEventFiles.add(file);
				cancelledKeys.add(data.cancelledKey());
			}
		});

		List<NfceRow> lines = new ArrayList<>();

		allData.forEach((file, data) -> {
			if (cancelEventFiles.contains(file)) {
				return;
			}

			String accessKey = file.replaceFirst("(?i)\\.xml$", "");

			String number;
			String series;
			String totalValue;
			String emissionDate;
			String status;

			if (data == null) {
				number = null;
				series = null;
				totalValue = null;
				emissionDate = null;
				status = "Erro na Leitura";
			} else {
				number = data.number();
				series = data.series();
				totalValue = data.totalValue();
				emissionDate = data.emissionDate();
				status = cancelledKeys.contains(accessKey) ? "Cancelada" : "Enviada";
			}

			lines.add(new NfceRow(number, series, totalValue, emissionDate, accessKey, status));
		});

		return lines;
	}

	private static ObservableList<ObservableList<String>> buildMissingRows(List<List<String>> missingByColumn) {

		int maxRows = missingByColumn.stream().mapToInt(List::size).max().orElse(0);

		ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

		for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
			ObservableList<String> row = FXCollections.observableArrayList();

			for (List<String> columnMissing : missingByColumn) {
				row.add(rowIndex < columnMissing.size() ? columnMissing.get(rowIndex) : "");
			}

			rows.add(row);
		}

		return rows;
	}
}