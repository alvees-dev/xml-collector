package com.alveesdev.xmlcollector.infra.xml;

import java.time.LocalDate;

import org.w3c.dom.Document;

import com.alveesdev.xmlcollector.domain.model.NfceFileData;

public class XmlRowReader {

    public static NfceFileData read(Document file) {
        String number = XmlExtractor.getNfNumber(file);
        String series = XmlExtractor.getNfSeries(file);
        String totalValue = XmlExtractor.getNfTotalValue(file);
        String emissionDate = XmlExtractor.getNfEmissionDate(file);
        String cancelledKey = XmlCancelReader.getCancelledNfKey(file);
        LocalDate documentDate = XmlDateReader.getDocumentDate(file);
        String accessKey = XmlKeyReader.getAccessKey(file);

        return new NfceFileData(number, series, totalValue, emissionDate, cancelledKey, documentDate, accessKey);
    }
}