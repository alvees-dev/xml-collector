package com.alveesdev.xmlcollector.ui.model;

import java.nio.file.Path;

import com.alveesdev.xmlcollector.application.port.Failures;

public final class FailureText {

	public static final String NO_DESCRIPTION = "Ocorreu um erro sem descrição.";

	private FailureText() {
	}

	public static String of(Throwable error) {
		return Failures.describe(error);
	}

	public static String orNoDescription(String message) {
		return message == null || message.isBlank() ? NO_DESCRIPTION : message;
	}

	public static String unexpected(Throwable error, Path logDirectory) {
		return "Ocorreu um erro inesperado: " + of(error) + "\n\nOs detalhes foram registrados em:\n" + logDirectory;
	}
}
