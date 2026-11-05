package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.concurrent.Task;
import javafx.scene.Node;

// Everything here answers on the thread of the screen, and a failure is always recorded before it is shown.
final class TaskRunner {

	private static final Logger LOG = Logger.getLogger(TaskRunner.class.getName());

	interface WindowOpener {
		ProgressWindow open() throws IOException;
	}

	record Handlers<T>(Consumer<T> onSuccess, Consumer<Throwable> onFailure, Runnable onCancelled) {
	}

	private TaskRunner() {
	}

	static <T> void withProgress(Node trigger, String threadName, WindowOpener opener, Function<ProgressWindow, Task<T>> work, Handlers<T> handlers) {

		ProgressWindow window;

		try {
			window = opener.open();
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "The progress window could not be opened", e);
			Dialogs.error("Erro ao abrir a tela de progresso: " + e.getMessage());
			return;
		}

		Task<T> task = work.apply(window);
		window.setCancelAction(task::cancel);

		Runnable finish = () -> {
			window.close();
			trigger.setDisable(false);
		};

		listen(task, threadName, finish, handlers);

		trigger.setDisable(true);
		window.show();

		start(task, threadName);
	}

	static <T> void inBackground(String threadName, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {

		Task<T> task = taskOf(work);

		listen(task, threadName, () -> {
		}, new Handlers<>(onSuccess, onFailure, () -> {
		}));

		start(task, threadName);
	}

	static <T> Task<T> taskOf(Callable<T> work) {
		return new Task<>() {

			@Override
			protected T call() throws Exception {
				return work.call();
			}
		};
	}

	private static <T> void listen(Task<T> task, String threadName, Runnable finish, Handlers<T> handlers) {

		task.setOnSucceeded(event -> {
			finish.run();
			handlers.onSuccess().accept(task.getValue());
		});

		task.setOnFailed(event -> {
			finish.run();
			LOG.log(Level.WARNING, "The task " + threadName + " failed", task.getException());
			handlers.onFailure().accept(task.getException());
		});

		task.setOnCancelled(event -> {
			finish.run();
			handlers.onCancelled().run();
		});
	}

	private static void start(Task<?> task, String threadName) {
		Thread thread = new Thread(task, threadName);
		thread.setDaemon(true);
		thread.start();
	}
}
