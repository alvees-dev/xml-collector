package com.alveesdev.xmlcollector.application.port;

public final class Failures {

	private Failures() {
	}

	// Some exceptions have no message at all, and an empty reason on the screen helps nobody.
	public static String describe(Throwable error) {
		String message = error.getMessage();

		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}
}
