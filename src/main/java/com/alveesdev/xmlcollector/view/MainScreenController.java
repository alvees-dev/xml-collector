package com.alveesdev.xmlcollector.view;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.xmlconfig.NfceFileData;
import com.alveesdev.xmlcollector.xmlconfig.XmlProcessor;
import com.alveesdev.xmlcollector.xmlconfig.XmlRowReader;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MainScreenController {

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

	@FXML
	public void initialize() {
		numberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));

		serieColumn.setCellValueFactory(new PropertyValueFactory<>("series"));

		totalValueColumn.setCellValueFactory(new PropertyValueFactory<>("totalValue"));

		emissionDateColumn.setCellValueFactory(new PropertyValueFactory<>("emissionDate"));

		nfKeyColumn.setCellValueFactory(new PropertyValueFactory<>("accessKey"));

		statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
	}

	@FXML
	private void onVerificarDiretorio() {

		String directory = txtDirectory.getText();

		btnVerificarDir.setDisable(true);

		Task<ObservableList<NfceRow>> task = new Task<>() {

			@Override
			protected ObservableList<NfceRow> call() throws Exception {

				Map<String, NfceFileData> allData = XmlProcessor.collectXml(directory, XmlRowReader::read);

				Set<String> cancelEventFiles = new HashSet<>();
				Set<String> cancelledKeys = new HashSet<>();
				allData.forEach((file, data) -> {
					if (data != null && data.cancelledKey() != null) {
						cancelEventFiles.add(file);
						cancelledKeys.add(data.cancelledKey());
					}
				});

				ObservableList<NfceRow> lines = FXCollections.observableArrayList();

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
		};

		task.setOnSucceeded(event -> {
			tableView.setItems(task.getValue());
			btnVerificarDir.setDisable(false);
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
	private void onOpenSettings() {
		try {
			FXMLLoader loader = new FXMLLoader(
					getClass().getResource("/com/alveesdev/xmlcollector/view/SettingsV0.1.fxml"));
			Scene scene = new Scene(loader.load());

			Stage settingsStage = new Stage();
			settingsStage.setTitle("Configurações");
			settingsStage.setScene(scene);
			settingsStage.setResizable(false);

			// Modal: blocks the main window until settings are closed
			settingsStage.initModality(Modality.APPLICATION_MODAL);
			settingsStage.initOwner(tableView.getScene().getWindow());

			settingsStage.showAndWait();
		} catch (IOException e) {
			new Alert(Alert.AlertType.ERROR, "Erro ao abrir as configurações: " + e.getMessage()).showAndWait();
		}
	}
}