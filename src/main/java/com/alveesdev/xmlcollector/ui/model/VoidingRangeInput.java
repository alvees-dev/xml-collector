package com.alveesdev.xmlcollector.ui.model;

public final class VoidingRangeInput {

	private VoidingRangeInput() {
	}

	// Only the initial number means a single note, so the final one follows it until the user writes another.
	public static String endAfterStartChanged(String oldStart, String newStart, String end) {
		return end.isBlank() || end.equals(oldStart) ? newStart : end;
	}
}
