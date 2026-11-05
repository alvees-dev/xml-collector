package com.alveesdev.xmlcollector.application.port;

import java.util.Objects;
import java.util.stream.Stream;

public final class Failures {

	private static final int MAX_CAUSES = 20;

	private Failures() {
	}

	// Some exceptions have no message at all, and an empty reason on the screen helps nobody.
	public static String describe(Throwable error) {
		String message = error.getMessage();

		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	// A checked exception is a failure the program expects, like a missing file or no network, and its message says what happened.
	// Anything else is a defect in the program, and only the place in the code can explain it.
	public static boolean isDefect(Throwable error) {
		return chainOf(error).anyMatch(link -> link instanceof RuntimeException || link instanceof Error);
	}

	// The limit is there because a cause can point back at an earlier one.
	public static Stream<Throwable> chainOf(Throwable error) {
		return Stream.iterate(error, Objects::nonNull, Throwable::getCause).limit(MAX_CAUSES);
	}

	public static boolean saysMoreThanItsMessage(Throwable error) {
		String message = describe(error);

		return isDefect(error) || chainOf(error).skip(1).anyMatch(cause -> !message.contains(describe(cause)));
	}
}
