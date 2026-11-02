package com.alveesdev.xmlcollector.domain.model;

import java.util.Optional;

public record AccessKey(String value) {

	private static final int LENGTH = 44;
	private static final int MODEL_START = 20;
	private static final int MODEL_END = 22;

	public AccessKey {
		if (value == null || value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("Invalid access key: " + value);
		}
	}

	public String modelCode() {
		return value.substring(MODEL_START, MODEL_END);
	}

	public Optional<DocumentModel> model() {
		return DocumentModel.fromCode(modelCode());
	}
}
