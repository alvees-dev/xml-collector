package com.alveesdev.xmlcollector.domain.model;

import java.util.Arrays;
import java.util.Optional;

public enum DocumentModel {

	NFE("55"),
	NFCE("65");

	private final String code;

	DocumentModel(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<DocumentModel> fromCode(String code) {
		return Arrays.stream(values())
				.filter(model -> model.code.equals(code))
				.findFirst();
	}
}
