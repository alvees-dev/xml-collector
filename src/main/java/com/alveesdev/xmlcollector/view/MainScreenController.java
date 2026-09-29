package com.alveesdev.xmlcollector.view;

import java.util.Map;

import com.alveesdev.xmlcollector.model.NfceRow;
import com.alveesdev.xmlcollector.xmlconfig.XmlExtractor;
import com.alveesdev.xmlcollector.xmlconfig.XmlProcessor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
    private TableColumn<NfceRow, String> nfKeyColumn;

    /**
     * Chamado automaticamente pelo FXMLLoader depois que os campos @FXML
     * já foram injetados. É aqui que se configura como cada coluna busca
     * seu valor dentro de um NfceRow.
     */
    @FXML
    public void initialize() {
        numberColumn.setCellValueFactory(
            new PropertyValueFactory<>("number")
        );

        serieColumn.setCellValueFactory(
            new PropertyValueFactory<>("series")
        );

        nfKeyColumn.setCellValueFactory(
            new PropertyValueFactory<>("accessKey")
        );
    }

    /**
     * Ligado ao onAction="#onVerificarDiretorio" do botão "Verificar Dir".
     */
    @FXML
    private void onVerificarDiretorio() {
        String directory = txtDirectory.getText();

        try {
            Map<String, String> nfNumber =
                    XmlProcessor.collectXml(directory, XmlExtractor::getNfNumber);
            
            Map<String, String> nfSeries =
                    XmlProcessor.collectXml(directory, XmlExtractor::getNfSeries);

            ObservableList<NfceRow> lines = FXCollections.observableArrayList();
           
            nfNumber.forEach((file, number) -> {
                String accessKey = file.replaceFirst("(?i)\\.xml$", "");
                String series = nfSeries.get(file);
                lines.add(new NfceRow(number, series, accessKey));
            });

            tableView.setItems(lines);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "Erro ao processar o diretório: " + e.getMessage()).showAndWait();
        }
    }
}