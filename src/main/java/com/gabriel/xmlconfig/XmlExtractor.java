package com.gabriel.xmlconfig;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlExtractor {

    public static String extrairNumeroNF(Document doc) {
    	
        NodeList xmlList = doc.getElementsByTagName("nNF");
        if (xmlList.getLength() > 0) {
            return xmlList.item(0).getTextContent().trim();
        }
        return "Não foram escontrados elementos com a TAG especificada";
    }
}