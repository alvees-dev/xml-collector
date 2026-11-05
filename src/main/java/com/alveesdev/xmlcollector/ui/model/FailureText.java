package com.alveesdev.xmlcollector.ui.model;

import java.nio.file.Path;

public final class FailureText {

	public static final String NO_DESCRIPTION = "Ocorreu um erro sem descrição.";

	private FailureText() {
	}

	public static String of(Throwable error) {
		String message = error.getMessage();

		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	public static String orNoDescription(String message) {
		return message == null || message.isBlank() ? NO_DESCRIPTION : message;
	}

	public static String unexpected(Throwable error, Path logDirectory) {
		return "Ocorreu um erro inesperado: " + of(error) + "\n\nOs detalhes foram registrados em:\n" + logDirectory;
	}
}
