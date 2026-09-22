package com.gabriel.xmlconfig;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

import java.io.File;

public class XmlReader {

	public static Document loadXML(File xmlArchive) throws Exception {

		DocumentBuilderFactory createXmlReader = DocumentBuilderFactory.newInstance();
		createXmlReader.setNamespaceAware(false);

		DocumentBuilder readXml = createXmlReader.newDocumentBuilder();

		Document xmlReaded = readXml.parse(xmlArchive);
		xmlReaded.getDocumentElement().normalize();
		return xmlReaded;
	}
}