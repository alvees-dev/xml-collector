package com.alveesdev.xmlcollector.ui.view;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.ui.model.FailureText;

import javafx.application.Platform;

public final class UnexpectedErrorHandler implements Thread.UncaughtExceptionHandler {

	private static final Logger LOG = Logger.getLogger(UnexpectedErrorHandler.class.getName());

	private final Path logDirectory;
	private final Executor uiThread;
	private final Consumer<String> dialog;
	private final AtomicBoolean showing = new AtomicBoolean();

	UnexpectedErrorHandler(Path logDirectory, Executor uiThread, Consumer<String> dialog) {
		this.logDirectory = logDirectory;
		this.uiThread = uiThread;
		this.dialog = dialog;
	}

	// Always later on the UI thread, because a dialog cannot wait in the middle of an animation or of a layout.
	public static void install(Path logDirectory) {
		Thread.setDefaultUncaughtExceptionHandler(new UnexpectedErrorHandler(logDirectory, Platform::runLater, Dialogs::error));
	}

	@Override
	public void uncaughtException(Thread thread, Throwable error) {

		LOG.log(Level.SEVERE, "Erro inesperado na thread " + thread.getName(), error);

		// An error of the screen can repeat many times a second, and each one would open a window over the last.
		if (!showing.compareAndSet(false, true)) {
			return;
		}

		try {
			uiThread.execute(() -> show(error));
		} catch (RuntimeException e) {
			showing.set(false);
			LOG.log(Level.WARNING, "O erro não pôde ser mostrado na tela", e);
		}
	}

	private void show(Throwable error) {
		try {
			dialog.accept(FailureText.unexpected(error, logDirectory));
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "O erro não pôde ser mostrado na tela", e);
		} finally {
			showing.set(false);
		}
	}
}
