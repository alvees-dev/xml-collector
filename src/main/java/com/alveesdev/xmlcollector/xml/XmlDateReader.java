package com.alveesdev.xmlcollector.xml;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlDateReader {

	private static final String[] DATE_TAGS = { "dhEmi", "dhEvento" };

	public static LocalDate getDocumentDate(Document file) {

		for (String tag : DATE_TAGS) {
			NodeList nodes = file.getElementsByTagName(tag);

			if (nodes.getLength() > 0) {
				LocalDate date = parse(nodes.item(0).getTextContent().trim());

				if (date != null) {
					return date;
				}
			}
		}

		return null;
	}

	private static LocalDate parse(String text) {
		try {
			return OffsetDateTime.parse(text).toLocalDate();
		} catch (DateTimeParseException offsetError) {
			return parseDatePart(text);
		}
	}

	private static LocalDate parseDatePart(String text) {
		if (text.length() < 10) {
			return null;
		}

		try {
			return LocalDate.parse(text.substring(0, 10));
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}