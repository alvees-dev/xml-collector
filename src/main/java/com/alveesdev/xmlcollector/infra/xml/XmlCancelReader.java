package com.alveesdev.xmlcollector.infra.xml;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlCancelReader {

	private static final String CANCEL_EVENT_TYPE = "110111";

    public static String getCancelledNfKey(Document file) {

    	NodeList xmlEventTypeList = file.getElementsByTagName("tpEvento");
    	if (xmlEventTypeList.getLength() == 0) {
    		return null;
    	}

    	String eventType = xmlEventTypeList.item(0).getTextContent().trim();
    	if (!CANCEL_EVENT_TYPE.equals(eventType)) {
    		return null;
    	}

    	NodeList xmlKeyList = file.getElementsByTagName("chNFe");
    	if (xmlKeyList.getLength() == 0) {
    		return null;
    	}

    	return xmlKeyList.item(0).getTextContent().trim();
    }
}