package com.alveesdev.xmlcollector.ui.model;

public final class SourceLabels {

	private SourceLabels() {
	}

	public static String label(int sourceIndex, SourceMode mode) {
		return switch (mode) {
			case PDV_DIRECTORIES -> String.format("PDV %02d", sourceIndex + 1);
			case NFE_DIRECTORIES -> String.format("NFe %02d", sourceIndex + 1);
			case SINGLE_DIRECTORY -> "Diretório";
			case SERVER_DIRECTORY -> "Servidor";
		};
	}

	// The name of the whole source, as the list on the main screen shows it.
	public static String name(SourceMode mode) {
		return switch (mode) {
			case SERVER_DIRECTORY -> "Diretório Servidor";
			case PDV_DIRECTORIES -> "Diretório PDV";
			case SINGLE_DIRECTORY -> "Diretório Único";
			case NFE_DIRECTORIES -> "Diretório NFe";
		};
	}

	public static String header(int sourceIndex) {
		return String.format("SERIE%2d ", sourceIndex + 1);
	}
}
