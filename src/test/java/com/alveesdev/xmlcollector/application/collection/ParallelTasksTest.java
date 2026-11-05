package com.alveesdev.xmlcollector.application.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParallelTasksTest {

	private final ExecutorService executor = Executors.newFixedThreadPool(4);
	private final ParallelTasks tasks = new ParallelTasks(executor);

	@AfterEach
	void stop() {
		executor.shutdownNow();
	}

	@Test
	void theResultsComeInTheOrderOfTheTasksEvenIfTheLastEndsFirst() {
		CountDownLatch lastEnded = new CountDownLatch(1);

		Callable<Integer> first = () -> {
			lastEnded.await();
			return 1;
		};
		Callable<Integer> last = () -> {
			lastEnded.countDown();
			return 2;
		};

		List<Integer> results = new ArrayList<>();
		tasks.runAll(List.of(first, last), results::add);

		assertEquals(List.of(1, 2), results);
	}

	@Test
	void anUncheckedFailureComesBackAsItIs() {
		IllegalArgumentException failure = new IllegalArgumentException("falhou");

		Callable<Void> task = () -> {
			throw failure;
		};

		assertSame(failure, assertThrows(IllegalArgumentException.class, () -> tasks.runAll(List.of(task), result -> {
		})));
	}

	@Test
	void anErrorComesBackAsItIs() {
		AssertionError failure = new AssertionError("falhou");

		Callable<Void> task = () -> {
			throw failure;
		};

		assertSame(failure, assertThrows(AssertionError.class, () -> tasks.runAll(List.of(task), result -> {
		})));
	}

	@Test
	void aCheckedFailureIsWrapped() {
		Callable<Void> task = () -> {
			throw new IOException("falhou");
		};

		IllegalStateException wrapped = assertThrows(IllegalStateException.class, () -> tasks.runAll(List.of(task), result -> {
		}));

		assertInstanceOf(IOException.class, wrapped.getCause());
	}

	@Test
	void beingInterruptedMeansCancelled() {
		CountDownLatch never = new CountDownLatch(1);

		Callable<Void> task = () -> {
			never.await();
			return null;
		};

		Thread.currentThread().interrupt();

		try {
			assertThrows(CancellationException.class, () -> tasks.runAll(List.of(task), result -> {
			}));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}
}
