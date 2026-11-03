package com.alveesdev.xmlcollector.domain.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AccessKey(String value) {

	private static final int LENGTH = 44;
	private static final Pattern KEY_IN_TEXT = Pattern.compile("(?<![0-9])[0-9]{44}(?![0-9])");
	private static final int CNPJ_START = 6;
	private static final int CNPJ_END = 20;
	private static final int MODEL_START = 20;
	private static final int MODEL_END = 22;
	private static final int SERIES_END = 25;
	private static final int NUMBER_END = 34;

	public AccessKey {
		if (value == null || value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("Invalid access key: " + value);
		}
	}

	public static Optional<AccessKey> findIn(String text) {
		Matcher matcher = KEY_IN_TEXT.matcher(text);
		return matcher.find() ? Optional.of(new AccessKey(matcher.group())) : Optional.empty();
	}

	public String cnpj() {
		return value.substring(CNPJ_START, CNPJ_END);
	}

	public String modelCode() {
		return value.substring(MODEL_START, MODEL_END);
	}

	public int series() {
		return Integer.parseInt(value.substring(MODEL_END, SERIES_END));
	}

	public int number() {
		return Integer.parseInt(value.substring(SERIES_END, NUMBER_END));
	}

	public Optional<DocumentModel> model() {
		return DocumentModel.fromCode(modelCode());
	}
}
