package com.alveesdev.xmlcollector.xmlconfig;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlExtractor {

	private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

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

	public static String getNfEmissionDate(Document file) {

		NodeList xmlDateList = file.getElementsByTagName("dhEmi");
		if (xmlDateList.getLength() == 0) {
			return "NULL";
		}

		String rawDate = xmlDateList.item(0).getTextContent().trim();
		try {
			return OffsetDateTime.parse(rawDate).format(DISPLAY_DATE_FORMAT);

		} catch (DateTimeParseException dateTimeParseException) {
			return rawDate;
		}
	}
}