package com.alveesdev.xmlcollector.domain.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AccessKey(String value) {

	// Since the alphanumeric CNPJ, the first twelve characters of a CNPJ can be capital letters. Everything else stays numeric.
	public static final String CNPJ_PATTERN = "[0-9A-Z]{12}[0-9]{2}";

	private static final String KEY_PATTERN = "[0-9]{6}" + CNPJ_PATTERN + "[0-9]{24}";
	private static final Pattern KEY_IN_TEXT = Pattern.compile("(?<![0-9])" + KEY_PATTERN + "(?![0-9])");
	private static final int UF_END = 2;
	private static final int YEAR_END = 4;
	private static final int CENTURY = 2000;
	private static final int CNPJ_START = 6;
	private static final int CNPJ_END = 20;
	private static final int MODEL_START = 20;
	private static final int MODEL_END = 22;
	private static final int SERIES_END = 25;
	private static final int NUMBER_END = 34;

	public AccessKey {
		if (value == null || !value.matches(KEY_PATTERN)) {
			throw new IllegalArgumentException("Invalid access key: " + value);
		}
	}

	public static Optional<AccessKey> findIn(String text) {
		Matcher matcher = KEY_IN_TEXT.matcher(text);
		return matcher.find() ? Optional.of(new AccessKey(matcher.group())) : Optional.empty();
	}

	public String ufCode() {
		return value.substring(0, UF_END);
	}

	public int year() {
		return CENTURY + Integer.parseInt(value.substring(UF_END, YEAR_END));
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
