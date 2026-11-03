package com.alveesdev.xmlcollector.domain.model;

public record SeriesOwner(String ufCode, String cnpj, int year) {

	public static SeriesOwner of(AccessKey key) {
		return new SeriesOwner(key.ufCode(), key.cnpj(), key.year());
	}
}
