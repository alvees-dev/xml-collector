package com.alveesdev.xmlcollector.application.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult.Outcome;
import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult.Status;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.domain.model.DateRange;

class DirectoryTesterTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T12:00:00Z"), ZoneOffset.UTC);
	private static final Path ROOT = Path.of("caixa");

	private final FakeSources sources = new FakeSources();
	private final FakeArchive archive = new FakeArchive();

	@Test
	void aBlankDirectoryIsNotInformed() {
		DirectoryTestResult result = tester().test(new DirectoryTarget("  ", List.of(), ""));

		assertEquals(Outcome.NOT_INFORMED, result.outcome());
		assertEquals(Status.FAILED, result.status());
	}

	@Test
	void everythingFoundAndWritableIsOk() {
		sources.folders = List.of(ROOT.resolve("enviados"));

		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of("enviados"), "inutilizadas"));

		assertEquals(Outcome.CONNECTED, result.outcome());
		assertEquals(Status.OK, result.status());
		assertEquals(List.of(ROOT.resolve("inutilizadas")), archive.tested);
	}

	@Test
	void anUnreachableDirectoryHasNoConnection() {
		sources.readFailure = new FileSystemException("caixa", null, "O caminho da rede não foi encontrado");

		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of("enviados"), ""));

		assertEquals(Outcome.NO_CONNECTION, result.outcome());
		assertEquals(Status.FAILED, result.status());
		assertEquals(List.of(), archive.tested);
	}

	@Test
	void aRefusedDirectoryHasNoPermission() {
		sources.readFailure = new AccessDeniedException("caixa");

		assertEquals(Outcome.NO_PERMISSION, tester().test(new DirectoryTarget("caixa", List.of(), "")).outcome());
	}

	@Test
	void aMissingFolderOfTheModelIsAWarningWithoutTheRoot() {
		sources.folders = List.of(ROOT.resolve("enviados"));
		sources.missing = List.of("caixa" + File.separator + "cancelados");

		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of("enviados", "cancelados"), ""));

		assertEquals(Status.WARNING, result.status());
		assertEquals(List.of("cancelados"), result.missingFolders());
	}

	@Test
	void noFolderOfTheModelIsAWarning() {
		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of("?enviados"), ""));

		assertEquals(Status.WARNING, result.status());
		assertFalse(result.anyModelFolder());
	}

	@Test
	void aFolderThatCannotBeWrittenIsAWarning() {
		archive.failure = new AccessDeniedException("caixa");

		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of(), ""));

		assertEquals(Status.WARNING, result.status());
		assertFalse(result.writable());
	}

	@Test
	void writingIsNotTestedWithoutAWritableFolder() {
		DirectoryTestResult result = tester().test(new DirectoryTarget("caixa", List.of(), null));

		assertEquals(Status.OK, result.status());
		assertEquals(List.of(), archive.tested);
	}

	@Test
	void usesThePreviousMonthToLookForTheFolders() {
		tester().test(new DirectoryTarget("caixa", List.of("enviados"), ""));

		assertEquals(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), sources.period);
	}

	@Test
	void hangingBeforeConnectingHasNoConnection() {
		sources.hangOnRead = new CountDownLatch(1);

		try {
			assertEquals(Outcome.NO_CONNECTION, fastTester().test(new DirectoryTarget("caixa", List.of(), "")).outcome());
		} finally {
			sources.hangOnRead.countDown();
		}
	}

	@Test
	void hangingAfterConnectingIsASlowSearch() {
		sources.hangOnSearch = new CountDownLatch(1);

		try {
			DirectoryTestResult result = fastTester().test(new DirectoryTarget("caixa", List.of("enviados"), ""));

			assertEquals(Outcome.SEARCH_TOO_SLOW, result.outcome());
			assertEquals(Status.WARNING, result.status());
		} finally {
			sources.hangOnSearch.countDown();
		}
	}

	private DirectoryTester tester() {
		return new DirectoryTester(sources, archive, CLOCK, Duration.ofSeconds(30));
	}

	private DirectoryTester fastTester() {
		return new DirectoryTester(sources, archive, CLOCK, Duration.ofMillis(200));
	}

	private static class FakeSources implements SourceFiles {

		private IOException readFailure;
		private CountDownLatch hangOnRead;
		private CountDownLatch hangOnSearch;
		private List<Path> folders = List.of();
		private List<String> missing = List.of();
		private DateRange period;

		@Override
		public void requireReadable(Path directory) throws IOException {
			await(hangOnRead);

			if (readFailure != null) {
				throw readFailure;
			}
		}

		@Override
		public ResolvedFolders resolveFolders(Path root, List<String> patterns, DateRange period) {
			await(hangOnSearch);
			this.period = period;
			return new ResolvedFolders(folders, List.of(), missing);
		}

		@Override
		public ScanResult scan(Path directory, Path excludedRoot) {
			throw new UnsupportedOperationException();
		}

		@Override
		public byte[] read(SourceFile file) {
			throw new UnsupportedOperationException();
		}

		private static void await(CountDownLatch latch) {
			if (latch == null) {
				return;
			}

			try {
				latch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static class FakeArchive implements DocumentArchive {

		private final List<Path> tested = new ArrayList<>();
		private IOException failure;

		@Override
		public Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
				String canonicalName, String identity) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void requireWritable(Path directory) throws IOException {
			tested.add(directory);

			if (failure != null) {
				throw failure;
			}
		}
	}
}
