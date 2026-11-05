package com.alveesdev.xmlcollector.ui.controller;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.concurrent.Task;

final class BackgroundWork {

	private BackgroundWork() {
	}

	// The two answers come back on the thread of the screen.
	static <T> Task<T> start(String threadName, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {

		Task<T> task = new Task<>() {

			@Override
			protected T call() throws Exception {
				return work.call();
			}
		};

		task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
		task.setOnFailed(event -> onFailure.accept(task.getException()));

		Thread thread = new Thread(task, threadName);
		thread.setDaemon(true);
		thread.start();

		return task;
	}
}
