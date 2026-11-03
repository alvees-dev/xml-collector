package com.alveesdev.xmlcollector.domain.model;

public enum SeparationMode {

	ALL_TOGETHER("Tudo junto"),
	BY_PDV("Por PDV");

	private final String label;

	SeparationMode(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
