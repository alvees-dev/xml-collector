package com.alveesdev.xmlcollector.domain.model;

public enum PdvType {

	PDV("PDV"),
	FOOD("Food");

	private final String label;

	PdvType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
