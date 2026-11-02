package com.alveesdev.xmlcollector.ui.model;

public final class SourceLabels {

	private SourceLabels() {
	}

	public static String label(int sourceIndex, SourceMode mode) {
		return mode == SourceMode.PDV_DIRECTORIES ? String.format("PDV %02d", sourceIndex + 1) : "Diretório";
	}

	public static String header(int sourceIndex) {
		return String.format("PDV%02d", sourceIndex + 1);
	}
}
