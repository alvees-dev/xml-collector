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

import com.alveesdev.xmlcollector.config.AppPaths;
import com.alveesdev.xmlcollector.config.AppSettings;
import com.alveesdev.xmlcollector.config.OrganizeMode;
import com.alveesdev.xmlcollector.model.NfceFileData;
import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.service.MissingNumberFinder;
import com.alveesdev.xmlcollector.service.PeriodFilter;
import com.alveesdev.xmlcollector.service.XmlOrganizer;
import com.alveesdev.xmlcollector.xml.XmlProcessor;
import com.alveesdev.xmlcollector.xml.XmlRowReader;

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
			List<String> failures,
			XmlOrganizer.CopyReport copyReport) {
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

		LocalDate from = dpFrom.getValue();
		LocalDate to = dpTo.getValue();

		if (from == null || to == null || from.isAfter(to)) {
			new Alert(Alert.AlertType.WARNING, "Informe um período válido (De / Até).").showAndWait();
			return;
		}

		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		if (!pdvMode && txtDirectory.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, "Informe o diretório a ser verificado.").showAndWait();
			return;
		}

		List<String> directories = resolveDirectories(pdvMode);
		OrganizeMode mode = AppSettings.getInstance().getOrganizeMode();
		Path archiveRoot = AppPaths.fromJarLocation().xmlArchiveDir();

		btnVerificarDir.setDisable(true);

		Task<CollectionResult> task = new Task<>() {

			@Override
			protected CollectionResult call() throws Exception {

				ObservableList<NfceRow> lines = FXCollections.observableArrayList();
				List<String> failures = new ArrayList<>();
				XmlOrganizer.CopyReport copyReport = new XmlOrganizer.CopyReport();

				if (!pdvMode) {
					String directory = directories.get(0);

					Map<String, NfceFileData> allData =
							XmlProcessor.collectXml(directory, XmlRowReader::read);

					lines.addAll(buildRows(allData, from, to));

					Map<String, NfceFileData> periodData = PeriodFilter.filter(allData, from, to);

					XmlOrganizer.copyDirectory(Path.of(directory), from, to, archiveRoot, mode, copyReport);

					List<String> seriesHeaders = new ArrayList<>();
					List<List<String>> missingBySeries = new ArrayList<>();

					MissingNumberFinder.findMissingBySeries(periodData).forEach((series, ranges) -> {
						seriesHeaders.add("SERIE " + series);
						missingBySeries.add(ranges);
					});

					return new CollectionResult(lines, seriesHeaders, buildMissingRows(missingBySeries),
							failures, copyReport);
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

						List<NfceRow> pdvLines = buildRows(allData, from, to);
						List<String> pdvMissing =
								MissingNumberFinder.findMissing(PeriodFilter.filter(allData, from, to));

						XmlOrganizer.copyDirectory(Path.of(pdvDirectory), from, to, archiveRoot, mode, copyReport);

						lines.addAll(pdvLines);
						missingByPdv.add(pdvMissing);

					} catch (Exception e) {
						failures.add(pdvLabel + ": diretório inacessível (" + pdvDirectory + ")");
						missingByPdv.add(List.of());
					}
				}

				return new CollectionResult(lines, pdvHeaders(directories.size()),
						buildMissingRows(missingByPdv), failures, copyReport);
			}
		};

		task.setOnSucceeded(event -> {
			CollectionResult result = task.getValue();

			tableView.setItems(result.lines());

			missingShowsSeries = !pdvMode;
			showMissingColumns(result.missingHeaders());
			missingTableView.setItems(result.missingRows());

			btnVerificarDir.setDisable(false);

			XmlOrganizer.CopyReport report = result.copyReport();

			List<String> allFailures = new ArrayList<>(result.failures());
			allFailures.addAll(report.getFailures());

			StringBuilder message = new StringBuilder();
			message.append("Copiados: ").append(report.getCopied());
			message.append("\nJá existiam no destino: ").append(report.getAlreadyExisting());
			message.append("\nFora do período: ").append(report.getOutOfPeriod());
			message.append("\nSem data ou ilegíveis: ").append(report.getUnreadable());
			message.append("\n\nDestino: ").append(archiveRoot);

			if (!allFailures.isEmpty()) {
				message.append("\n\nFalhas:\n").append(String.join("\n", allFailures));
			}

			Alert alert = new Alert(
					allFailures.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
					message.toString());
			alert.setHeaderText("Coleta concluída");
			alert.showAndWait();
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

	private static List<NfceRow> buildRows(Map<String, NfceFileData> allData, LocalDate from, LocalDate to) {

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

			if (!PeriodFilter.isInPeriod(file, data, from, to)) {
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