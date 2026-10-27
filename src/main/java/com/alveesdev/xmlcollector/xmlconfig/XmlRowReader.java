package com.alveesdev.xmlcollector.xmlconfig;

import org.w3c.dom.Document;

public class XmlRowReader {

    public static NfceFileData read(Document file) {
        String number = XmlExtractor.getNfNumber(file);
        String series = XmlExtractor.getNfSeries(file);
        String totalValue = XmlExtractor.getNfTotalValue(file);
        String emissionDate = XmlExtractor.getNfEmissionDate(file);
        String cancelledKey = XmlCancelReader.getCancelledNfKey(file);

        return new NfceFileData(number, series, totalValue, emissionDate, cancelledKey);
    }
}
