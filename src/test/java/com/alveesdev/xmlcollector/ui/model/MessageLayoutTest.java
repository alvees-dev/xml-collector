package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessageLayoutTest {

	@Test
	void aShortMessageKeepsTheMinimumHeight() {
		assertEquals(MessageLayout.MIN_ROWS, MessageLayout.rowsFor("Coleta cancelada."));
	}

	@Test
	void countsEveryLineIncludingTheBlankOnes() {
		assertEquals(4, MessageLayout.rowsFor("a\n\nb\nc"));
	}

	@Test
	void countsTheRowsOfALineThatWraps() {
		assertEquals(3, MessageLayout.rowsFor("x".repeat(171)));
	}

	@Test
	void aVeryLongMessageStopsAtTheMaximumAndScrolls() {
		assertEquals(MessageLayout.MAX_ROWS, MessageLayout.rowsFor("linha\n".repeat(500)));
	}

	@Test
	void aMessageThatFitsInThePreviewIsShownWhole() {
		String message = "a\n".repeat(MessageLayout.PREVIEW_ROWS - 1) + "b";

		assertFalse(MessageLayout.isLong(message));
		assertEquals(message, MessageLayout.preview(message));
	}

	@Test
	void theLongMessageIsCutAfterThePreviewRows() {
		String message = "a\n".repeat(MessageLayout.PREVIEW_ROWS) + "fim";

		assertTrue(MessageLayout.isLong(message));
		assertEquals("a\n".repeat(MessageLayout.PREVIEW_ROWS).stripTrailing() + "...", MessageLayout.preview(message));
	}

	@Test
	void cutsInsideALineThatDoesNotFit() {
		String preview = MessageLayout.preview("x".repeat(5000));

		assertTrue(preview.length() < 5000);
		assertTrue(preview.endsWith("x..."));
		assertEquals(MessageLayout.PREVIEW_ROWS, MessageLayout.rowsFor(preview.substring(0, preview.length() - 3)));
	}
}
