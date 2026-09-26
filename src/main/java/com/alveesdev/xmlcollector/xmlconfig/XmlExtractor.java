package com.alveesdev.xmlcollector.xmlconfig;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlExtractor {

    public static String getNfNumber(Document file) {
    	
        NodeList xmlList = file.getElementsByTagName("nNF");
        if (xmlList.getLength() > 0) {
            return xmlList.item(0).getTextContent().trim();
        }
        return "Não foram escontrados elementos com a TAG especificada";
    }
}