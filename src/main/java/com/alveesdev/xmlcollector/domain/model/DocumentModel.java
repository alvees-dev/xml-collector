package com.alveesdev.xmlcollector.domain.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum DocumentModel {

	NFE("55"),
	NFCE("65");

	public static final Set<DocumentModel> ALL = Set.of(values());

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
