package com.alveesdev.xmlcollector.model;

import javafx.beans.property.SimpleStringProperty;

/**
 * Represents a TableView row: the three fields shown per invoice
 * (number, series, access key). It's the "bridge" between the data
 * extracted from the XML (xmlconfig package) and what the screen shows.
 *
 * Uses JavaFX properties (SimpleStringProperty) instead of plain String
 * fields because that's the format TableColumn.setCellValueFactory
 * expects to display (and, in the future, edit) values in the table.
 */
public class NfceRow {

    private final SimpleStringProperty number;
    private final SimpleStringProperty series;
    private final SimpleStringProperty emissionDate;
    private final SimpleStringProperty accessKey;

    public NfceRow(String number, String series, String emissionDate,String accessKey) {
        this.number = new SimpleStringProperty(number);
        this.series = new SimpleStringProperty(series);
        this.emissionDate = new SimpleStringProperty(emissionDate);
        this.accessKey = new SimpleStringProperty(accessKey);
    }

    public String getNumber() {
        return number.get();
    }

    public SimpleStringProperty numberProperty() {
        return number;
    }

    public String getSeries() {
        return series.get();
    }

    public SimpleStringProperty seriesProperty() {
        return series;
    }
    
    public String getEmissionDate() {
        return emissionDate.get();
    }

    public SimpleStringProperty emissionDateProperty() {
        return emissionDate;
    }

    public String getAccessKey() {
        return accessKey.get();
    }

    public SimpleStringProperty accessKeyProperty() {
        return accessKey;
    }
}