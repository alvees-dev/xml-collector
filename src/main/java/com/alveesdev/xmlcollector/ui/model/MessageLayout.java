package com.alveesdev.xmlcollector.ui.model;

public final class MessageLayout {

	public static final int MIN_ROWS = 2;
	public static final int PREVIEW_ROWS = 10;
	public static final int MAX_ROWS = 26;

	private static final String CUT_MARK = "...";

	// Below what really fits in a row, so a wrapped message gets a row too many and not one too few.
	private static final int CHARACTERS_PER_ROW = 85;

	private MessageLayout() {
	}

	public static int rowsFor(String message) {
		return Math.min(Math.max(countRows(message), MIN_ROWS), MAX_ROWS);
	}

	public static boolean isLong(String message) {
		return countRows(message) > PREVIEW_ROWS;
	}

	public static String preview(String message) {

		if (!isLong(message)) {
			return message;
		}

		StringBuilder preview = new StringBuilder();
		int rows = 0;

		for (String line : message.split("\n", -1)) {
			int lineRows = rowsOf(line);

			if (rows + lineRows > PREVIEW_ROWS) {
				preview.append(line, 0, (PREVIEW_ROWS - rows) * CHARACTERS_PER_ROW);
				break;
			}

			preview.append(line).append('\n');
			rows += lineRows;
		}

		return preview.toString().stripTrailing() + CUT_MARK;
	}

	private static int countRows(String message) {

		int rows = 0;

		for (String line : message.split("\n", -1)) {
			rows += rowsOf(line);
		}

		return rows;
	}

	private static int rowsOf(String line) {
		return Math.max(1, (line.length() + CHARACTERS_PER_ROW - 1) / CHARACTERS_PER_ROW);
	}
}
