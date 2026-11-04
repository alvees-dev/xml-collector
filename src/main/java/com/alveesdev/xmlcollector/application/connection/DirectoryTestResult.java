package com.alveesdev.xmlcollector.application.connection;

import java.util.List;

public record DirectoryTestResult(Outcome outcome, boolean anyModelFolder, List<String> missingFolders, boolean writable) {

	public enum Outcome {
		NOT_INFORMED,
		NO_CONNECTION,
		NO_PERMISSION,
		SEARCH_TOO_SLOW,
		CONNECTED
	}

	public enum Status {
		OK,
		WARNING,
		FAILED
	}

	public DirectoryTestResult {
		missingFolders = List.copyOf(missingFolders);
	}

	public static DirectoryTestResult of(Outcome outcome) {
		return new DirectoryTestResult(outcome, true, List.of(), true);
	}

	public Status status() {
		return switch (outcome) {
			case NOT_INFORMED, NO_CONNECTION, NO_PERMISSION -> Status.FAILED;
			case SEARCH_TOO_SLOW -> Status.WARNING;
			case CONNECTED -> anyModelFolder && missingFolders.isEmpty() && writable ? Status.OK : Status.WARNING;
		};
	}
}
