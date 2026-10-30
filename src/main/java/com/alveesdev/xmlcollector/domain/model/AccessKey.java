package com.alveesdev.xmlcollector.domain.model;

public record AccessKey(String value) {

	public static final String NFCE_MODEL = "65";

	private static final int LENGTH = 44;
	private static final int MODEL_START = 20;
	private static final int MODEL_END = 22;

	public AccessKey {
		if (value == null || value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("Invalid access key: " + value);
		}
	}

	public String model() {
		return value.substring(MODEL_START, MODEL_END);
	}

	public boolean isNfce() {
		return NFCE_MODEL.equals(model());
	}
}