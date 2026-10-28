package com.alveesdev.xmlcollector.model;

public record NfceEntry(
		String number,
		String series,
		String totalValue,
		String emissionDate,
		String accessKey,
		String status
) {
}