package com.alveesdev.xmlcollector.ui.model;

import com.alveesdev.xmlcollector.domain.model.DocumentEntry;

import javafx.beans.property.SimpleStringProperty;

public class DocumentRow {

    private final DocumentEntry entry;
    private final SimpleStringProperty number;
    private final SimpleStringProperty series;
    private final SimpleStringProperty totalValue;
    private final SimpleStringProperty emissionDate;
    private final SimpleStringProperty accessKey;
    private final SimpleStringProperty status;

    public DocumentRow(DocumentEntry entry, String number, String series, String totalValue, String emissionDate, String accessKey, String status) {
        this.entry = entry;
        this.number = new SimpleStringProperty(number);
        this.series = new SimpleStringProperty(series);
        this.totalValue = new SimpleStringProperty(totalValue);
        this.emissionDate = new SimpleStringProperty(emissionDate);
        this.accessKey = new SimpleStringProperty(accessKey);
        this.status = new SimpleStringProperty(status);
    }

    public DocumentEntry getEntry() {
        return entry;
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
    
    public String getTotalValue() {
    	return totalValue.get();
    }
    
    public SimpleStringProperty totalValueProperty() {
    	return totalValue;
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
    
    public String getStatus() {
    	return status.get();
    }
    
    public SimpleStringProperty statusProperty() {
    	return status;
    }
}