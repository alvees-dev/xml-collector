package com.alveesdev.xmlcollector.application.settings;

// The colors of the program. A dark theme also darkens the bars that Windows draws.
public enum Theme {

	DEEP_BLUE("Azul Profundo", false),
	NIGHT("Noite XML", true),
	DISCREET("Discreto", false);

	public static final Theme DEFAULT = DEEP_BLUE;

	private final String label;
	private final boolean dark;

	Theme(String label, boolean dark) {
		this.label = label;
		this.dark = dark;
	}

	public String label() {
		return label;
	}

	public boolean isDark() {
		return dark;
	}
}
