package com.alveesdev.xmlcollector.infra.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.domain.model.DateRange;

class SearchFolderResolverTest {

	private static final DateRange JANUARY_TO_FEBRUARY = new DateRange(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 5));

	@TempDir
	Path root;

	@Test
	void resolvesFixedFoldersAndSkipsTheAbsentOnes() throws IOException {
		create("a/enviados");

		assertEquals(Set.of(root.resolve("a/enviados")), resolve("a/enviados", "a/cancelados"));
	}

	@Test
	void keepsAccentsInFolderNames() throws IOException {
		create("produção/inutilização");

		assertEquals(Set.of(root.resolve("produção/inutilização")), resolve("produção/inutilização"));
	}

	@Test
	void findsTheMonthsOfThePeriodAtAnyDepth() throws IOException {
		create("arq/202601/docs");
		create("arq/x/202602/docs");
		create("arq/x/y/202602/docs");
		create("arq/x/202603/docs");

		assertEquals(
				Set.of(root.resolve("arq/202601/docs"), root.resolve("arq/x/202602/docs"), root.resolve("arq/x/y/202602/docs")),
				resolve("arq/**/{AAAAMM}/docs"));
	}

	@Test
	void doesNotSearchInsideMonthFoldersOutOfThePeriod() throws IOException {
		create("arq/202603/202601/docs");

		assertEquals(Set.of(), resolve("arq/**/{AAAAMM}/docs"));
	}

	@Test
	void failsWhenTheRootDoesNotExist() {
		assertThrows(NotDirectoryException.class, () -> SearchFolderResolver.resolve(
				root.resolve("ausente"), List.of("a"), JANUARY_TO_FEBRUARY));
	}

	private void create(String folder) throws IOException {
		Files.createDirectories(root.resolve(folder));
	}

	private Set<Path> resolve(String... patterns) throws IOException {
		return SearchFolderResolver.resolve(root, List.of(patterns), JANUARY_TO_FEBRUARY).folders().stream()
				.collect(Collectors.toSet());
	}
}
