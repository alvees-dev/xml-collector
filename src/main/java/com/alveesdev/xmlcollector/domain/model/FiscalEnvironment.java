package com.alveesdev.xmlcollector.domain.model;

public enum FiscalEnvironment {

	PRODUCTION("1"),
	HOMOLOGATION("2");

	private final String code;

	FiscalEnvironment(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}
}
