package com.alveesdev.xmlcollector.view;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.model.AppSettings;
import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.xmlconfig.MissingNumberFinder;
import com.alveesdev.xmlcollector.xmlconfig.NfceFileData;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainScreenController {

	// Must match the exact file name inside src/main/resources
	private static final String SETTINGS_FXML = "/com/alveesdev/xmlcollector/view/SettingsV0.1.fxml";

	private static final double MISSING_COLUMN_WIDTH = 110;

	/** What one collection run produces, ready to be shown on screen. */
	private record CollectionResult(
			ObservableList<NfceRow> lines,
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

	// Each row holds one cell per PDV column (same order as the PDVs in the settings)
	@FXML
	private TableView<ObservableList<String>> missingTableView;

	@FXML
	public void initialize() {
		numberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));

		serieColumn.setCellValueFactory(new PropertyValueFactory<>("series"));

		totalValueColumn.setCellValueFactory(new PropertyValueFactory<>("totalValue"));

		emissionDateColumn.setCellValueFactory(new PropertyValueFactory<>("emissionDate"));

		nfKeyColumn.setCellValueFactory(new PropertyValueFactory<>("accessKey"));

		statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

		refreshMissingColumns();
	}

	/** Rebuilds the "Faltantes" columns: one per PDV configured in the settings. */
	private void refreshMissingColumns() {
		int pdvCount = AppSettings.getInstance().getPdvCount();

		// Same number of PDVs: keep the columns and the results already shown
		if (missingTableView.getColumns().size() == pdvCount) {
			return;
		}

		missingTableView.getColumns().clear();
		missingTableView.getItems().clear();

		for (int number = 1; number <= pdvCount; number++) {
			final int columnIndex = number - 1;

			TableColumn<ObservableList<String>, String> column =
					new TableColumn<>(String.format("PDV%02d", number));
			column.setPrefWidth(MISSING_COLUMN_WIDTH);
			column.setCellValueFactory(cell -> {
				ObservableList<String> row = cell.getValue();
				return new SimpleStringProperty(columnIndex < row.size() ? row.get(columnIndex) : "");
			});

			missingTableView.getColumns().add(column);
		}
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
			settingsStage.initStyle(StageStyle.UNDECORATED); // no OS title bar
			settingsStage.setTitle("Configurações");
			settingsStage.setScene(scene);
			settingsStage.setResizable(false);

			// Modal: blocks the main window until settings are closed
			settingsStage.initModality(Modality.APPLICATION_MODAL);
			settingsStage.initOwner(tableView.getScene().getWindow());

			settingsStage.showAndWait();

			// The PDV list may have changed while the settings were open
			refreshMissingColumns();

		} catch (IOException e) {
			new Alert(Alert.AlertType.ERROR, "Erro ao abrir as configurações: " + e.getMessage()).showAndWait();
		}
	}

	@FXML
	private void onVerificarDiretorio() {

		// "Caminho PDV": reads every PDV directory from the settings.
		// Otherwise: reads only the directory typed in the text field.
		boolean pdvMode = RadioButtonColetaXMLCaminhoPDV.isSelected();

		if (!pdvMode && txtDirectory.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, "Informe o diretório a ser verificado.").showAndWait();
			return;
		}

		List<String> directories = pdvMode
				? AppSettings.getInstance().getPdvDirectories()
				: List.of(txtDirectory.getText());

		btnVerificarDir.setDisable(true);

		Task<CollectionResult> task = new Task<>() {

			@Override
			protected CollectionResult call() throws Exception {

				ObservableList<NfceRow> lines = FXCollections.observableArrayList();
				List<String> failures = new ArrayList<>();

				// Single directory: same behavior as before, no missing-numbers columns
				if (!pdvMode) {
					Map<String, NfceFileData> allData =
							XmlProcessor.collectXml(directories.get(0), XmlRowReader::read);

					lines.addAll(buildRows(allData));
					return new CollectionResult(lines, FXCollections.observableArrayList(), failures);
				}

				// One entry per PDV, in the same order as the columns
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
						// Single pass over the directory: each file is read and
						// parsed once (XmlReader), and XmlRowReader extracts from it
						// everything needed (number, series, value, date and
						// cancellation status) in the same read.
						Map<String, NfceFileData> allData =
								XmlProcessor.collectXml(pdvDirectory, XmlRowReader::read);

						lines.addAll(buildRows(allData));
						missingByPdv.add(MissingNumberFinder.findMissing(allData));

					} catch (Exception e) {
						failures.add(pdvLabel + ": diretório inacessível (" + pdvDirectory + ")");
						missingByPdv.add(List.of());
					}
				}

				return new CollectionResult(lines, buildMissingRows(missingByPdv), failures);
			}
		};

		task.setOnSucceeded(event -> {
			CollectionResult result = task.getValue();

			tableView.setItems(result.lines());
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

	/** Turns the data read from one directory into table rows for the NFCe tab. */
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

		// The access key is the file name itself
		allData.forEach((file, data) -> {
			// A cancellation event file is not an invoice: it must not
			// become its own table row, it only marks the status of the
			// original invoice it references (via cancelledKeys).
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

	/** Lays the missing ranges of each PDV side by side: one column per PDV. */
	private static ObservableList<ObservableList<String>> buildMissingRows(List<List<String>> missingByPdv) {

		int maxRows = missingByPdv.stream().mapToInt(List::size).max().orElse(0);

		ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

		for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
			ObservableList<String> row = FXCollections.observableArrayList();

			for (List<String> pdvMissing : missingByPdv) {
				row.add(rowIndex < pdvMissing.size() ? pdvMissing.get(rowIndex) : "");
			}

			rows.add(row);
		}

		return rows;
	}
}