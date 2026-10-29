package com.alveesdev.xmlcollector.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlKeyReader {

	private static final String ID_PREFIX = "NFe";
	private static final int ACCESS_KEY_LENGTH = 44;

	public static String getAccessKey(Document file) {

		NodeList infNFeList = file.getElementsByTagName("infNFe");

		if (infNFeList.getLength() > 0) {
			String id = ((Element) infNFeList.item(0)).getAttribute("Id").trim();
			String key = id.startsWith(ID_PREFIX) ? id.substring(ID_PREFIX.length()) : id;

			if (isValidKey(key)) {
				return key;
			}
		}

		NodeList protocolKeyList = file.getElementsByTagName("chNFe");

		if (protocolKeyList.getLength() > 0) {
			String key = protocolKeyList.item(0).getTextContent().trim();

			if (isValidKey(key)) {
				return key;
			}
		}

		return null;
	}

	public static boolean isValidKey(String key) {
		return key != null
				&& key.length() == ACCESS_KEY_LENGTH
				&& key.chars().allMatch(Character::isDigit);
	}
}