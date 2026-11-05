package com.alveesdev.xmlcollector.application.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

final class ParallelTasks {

	private final ExecutorService executor;

	ParallelTasks(ExecutorService executor) {
		this.executor = executor;
	}

	// The results arrive in the order of the tasks, so what depends on that order does not depend on the threads.
	<T> void runAll(List<Callable<T>> tasks, Consumer<T> onResult) {

		List<Future<T>> futures = new ArrayList<>();

		for (Callable<T> task : tasks) {
			futures.add(executor.submit(task));
		}

		try {
			for (Future<T> future : futures) {
				onResult.accept(future.get());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CancellationException("Coleta cancelada");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();

			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		} finally {
			for (Future<T> future : futures) {
				future.cancel(true);
			}
		}
	}
}
