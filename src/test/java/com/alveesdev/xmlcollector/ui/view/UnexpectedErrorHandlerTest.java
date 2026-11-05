package com.alveesdev.xmlcollector.ui.view;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.LogCapture;

class UnexpectedErrorHandlerTest {

	private static final Path LOGS = Path.of("Logs");
	private static final Thread THREAD = new Thread(() -> {
	}, "tela");

	private final List<String> shown = new ArrayList<>();
	private final Queue<Runnable> uiThread = new ArrayDeque<>();

	@Test
	void recordsTheErrorWithItsStack() {
		IllegalStateException error = new IllegalStateException("falhou");

		try (LogCapture log = new LogCapture(UnexpectedErrorHandler.class)) {
			handler().uncaughtException(THREAD, error);

			LogRecord entry = log.records().get(0);

			assertEquals(Level.SEVERE, entry.getLevel());
			assertTrue(entry.getMessage().contains("tela"), entry.getMessage());
			assertSame(error, entry.getThrown());
		}
	}

	@Test
	void showsTheErrorOnTheUiThreadOnly() {
		handler().uncaughtException(THREAD, new IllegalStateException("falhou"));

		assertEquals(List.of(), shown);

		uiThread.remove().run();

		assertEquals(1, shown.size());
		assertTrue(shown.get(0).contains("falhou"), shown.get(0));
		assertTrue(shown.get(0).contains("Logs"), shown.get(0));
	}

	@Test
	void errorsThatRepeatWhileOneIsOnTheScreenAreOnlyRecorded() {
		UnexpectedErrorHandler handler = handler();

		try (LogCapture log = new LogCapture(UnexpectedErrorHandler.class)) {
			handler.uncaughtException(THREAD, new IllegalStateException("primeiro"));
			handler.uncaughtException(THREAD, new IllegalStateException("segundo"));

			assertEquals(1, uiThread.size());
			assertEquals(2, log.records().size());
		}
	}

	@Test
	void theNextErrorIsShownAfterTheFirstIsClosed() {
		UnexpectedErrorHandler handler = handler();

		handler.uncaughtException(THREAD, new IllegalStateException("primeiro"));
		uiThread.remove().run();
		handler.uncaughtException(THREAD, new IllegalStateException("segundo"));
		uiThread.remove().run();

		assertEquals(2, shown.size());
	}

	@Test
	void anErrorWhenTheScreenIsGoneIsStillRecorded() {
		UnexpectedErrorHandler handler = new UnexpectedErrorHandler(LOGS, task -> {
			throw new IllegalStateException("Toolkit not initialized");
		}, shown::add);

		try (LogCapture log = new LogCapture(UnexpectedErrorHandler.class)) {
			assertDoesNotThrow(() -> handler.uncaughtException(THREAD, new IllegalStateException("falhou")));

			assertTrue(log.has(Level.SEVERE, "tela"));
			assertTrue(log.has(Level.WARNING, "could not be shown"));
		}
	}

	@Test
	void aDialogThatFailsDoesNotBlockTheNextOne() {
		List<String> attempts = new ArrayList<>();
		UnexpectedErrorHandler handler = new UnexpectedErrorHandler(LOGS, uiThread::add, text -> {
			attempts.add(text);
			throw new IllegalStateException("janela");
		});

		handler.uncaughtException(THREAD, new IllegalStateException("primeiro"));
		uiThread.remove().run();
		handler.uncaughtException(THREAD, new IllegalStateException("segundo"));
		uiThread.remove().run();

		assertEquals(2, attempts.size());
	}

	private UnexpectedErrorHandler handler() {
		return new UnexpectedErrorHandler(LOGS, uiThread::add, shown::add);
	}
}
