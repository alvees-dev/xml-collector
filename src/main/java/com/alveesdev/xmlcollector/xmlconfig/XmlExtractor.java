package com.alveesdev.xmlcollector.xmlconfig;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlExtractor {

	private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
	private static final NumberFormat DISPLAY_VALUE_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

	public static String getNfNumber(Document file) {

		NodeList xmlNumberList = file.getElementsByTagName("nNF");
		
		if (xmlNumberList.getLength() > 0) {
			return xmlNumberList.item(0).getTextContent().trim();
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

		String date = xmlDateList.item(0).getTextContent().trim();
		
		try {
			return OffsetDateTime.parse(date).format(DISPLAY_DATE_FORMAT);

		} catch (DateTimeParseException dateTimeParseException) {
			return date;
		}
	}
	
	public static String getNfTotalValue(Document file) {
		
		NodeList xmlTotalValueList = file.getElementsByTagName("vNF");
		
		if(xmlTotalValueList.getLength() == 0) {
			return "NULL";
		}
		
		String value = xmlTotalValueList.item(0).getTextContent().trim();
		
		try {
			return DISPLAY_VALUE_FORMAT.format(new BigDecimal(value));

		} catch (NumberFormatException numberFormatException) {
			return value;
		}
	}
}