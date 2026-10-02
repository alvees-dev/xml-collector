package com.alveesdev.xmlcollector.view;

import java.util.Map;

import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.xmlconfig.XmlExtractor;
import com.alveesdev.xmlcollector.xmlconfig.XmlProcessor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

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
	private TableColumn<NfceRow, String> emissionDateColumn;

	@FXML
	private TableColumn<NfceRow, String> nfKeyColumn;

	@FXML
	public void initialize() {
		numberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));

		serieColumn.setCellValueFactory(new PropertyValueFactory<>("series"));

		emissionDateColumn.setCellValueFactory(new PropertyValueFactory<>("emissionDate"));

		nfKeyColumn.setCellValueFactory(new PropertyValueFactory<>("accessKey"));
	}

	/**
	 * Ligado ao onAction="#onVerificarDiretorio" do botão "Verificar Dir". O
	 * processamento roda numa Task, em thread separada da UI
	 */
	@FXML
	private void onVerificarDiretorio() {

		String directory = txtDirectory.getText();

		btnVerificarDir.setDisable(true);

		Task<ObservableList<NfceRow>> task = new Task<>() {

			@Override
			protected ObservableList<NfceRow> call() throws Exception {

				Map<String, String> nfNumber = XmlProcessor.collectXml(directory, XmlExtractor::getNfNumber);

				Map<String, String> nfSeries = XmlProcessor.collectXml(directory, XmlExtractor::getNfSeries);
				
				Map<String, String> nfEmissionDate = XmlProcessor.collectXml(directory, XmlExtractor::getNfEmissionDate);

				ObservableList<NfceRow> lines = FXCollections.observableArrayList();

				// A chave de acesso é o próprio nome do arquivo
				nfNumber.forEach((file, number) -> {
					String series = nfSeries.get(file);
					String emissionDate = nfEmissionDate.get(file);
					String accessKey = file.replaceFirst("(?i)\\.xml$", "");
					lines.add(new NfceRow(number, series, emissionDate,accessKey));
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
}