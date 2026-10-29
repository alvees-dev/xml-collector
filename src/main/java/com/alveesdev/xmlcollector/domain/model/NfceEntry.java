package com.alveesdev.xmlcollector.domain.model;

public record NfceEntry(
		String number,
		String series,
		String totalValue,
		String emissionDate,
		String accessKey,
		String status
) {
}