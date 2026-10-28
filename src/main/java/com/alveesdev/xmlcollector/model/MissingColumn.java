package com.alveesdev.xmlcollector.model;

import java.util.List;

public record MissingColumn(String header, List<String> ranges) {

	public static String pdvHeader(int number) {
		return String.format("PDV%02d", number);
	}
}