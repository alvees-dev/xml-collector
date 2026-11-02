package com.alveesdev.xmlcollector.application.settings;

public enum EmailSecurity {

	STARTTLS("STARTTLS"),
	SSL("SSL/TLS");

	private final String label;

	EmailSecurity(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}