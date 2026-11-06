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

	public static String header(int sourceIndex) {
		return String.format("SERIE%2d ", sourceIndex + 1);
	}
}
