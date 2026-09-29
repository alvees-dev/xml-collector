package com.alveesdev.xmlcollector.xmlconfig;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlExtractor {

    public static String getNfNumber(Document file) {
    	
        NodeList xmlNfNumberList = file.getElementsByTagName("nNF");
        if (xmlNfNumberList.getLength() > 0) {
            return xmlNfNumberList.item(0).getTextContent().trim();
        }
        return "Número da NFCe não encontrado";
    }
    
    public static String getNfSeries(Document file) {
    	NodeList xmlSerieList = file.getElementsByTagName("serie");
    	if (xmlSerieList.getLength() > 0) {
    		return xmlSerieList.item(0).getTextContent().trim();
    	}
    	return "Serie não encontrada";
    }
}