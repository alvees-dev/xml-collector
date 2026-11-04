package com.alveesdev.xmlcollector.domain.model;

import java.time.LocalDate;

// The date is null only for what was saved before the history started keeping it.
public record IssuedNumber(String cnpj, String modelCode, int series, int number, LocalDate date) {

	public IssuedNumber(String cnpj, String modelCode, int series, int number) {
		this(cnpj, modelCode, series, number, null);
	}

	public static IssuedNumber of(XmlDocument.Invoice invoice) {
		AccessKey key = invoice.key();
		return new IssuedNumber(key.cnpj(), key.modelCode(), invoice.series(), invoice.number(), invoice.date());
	}
}
