package com.alveesdev.xmlcollector.domain.model;

public record IssuedNumber(String cnpj, String modelCode, int series, int number) {

	public static IssuedNumber of(XmlDocument.Invoice invoice) {
		AccessKey key = invoice.key();
		return new IssuedNumber(key.cnpj(), key.modelCode(), invoice.series(), invoice.number());
	}
}
