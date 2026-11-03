package com.alveesdev.xmlcollector.application.voiding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class VoidingPlannerTest {

	private static final SeriesOwner OWNER = new SeriesOwner("35", "12345678000190", 2026);
	private static final Map<Integer, Set<SeriesOwner>> OWNERS = Map.of(1, Set.of(OWNER));
	private static final Map<Integer, List<NumberRange>> MISSING = Map.of(1, List.of(new NumberRange(10, 12)));

	private Set<Integer> issued = Set.of();

	private final VoidingPlanner planner = new VoidingPlanner(new IssuedNumberRepository() {

		@Override
		public void initialize() {
		}

		@Override
		public void saveAll(Collection<IssuedNumber> numbers) {
		}

		@Override
		public Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) {
			return issued;
		}
	});

	@Test
	void buildsARequestWithTheDataOfTheSeriesAndTheStandardJustification() throws Exception {
		List<VoidingRequest> requests = planner.plan(Map.of(1, List.of(new NumberRange(11, 12))), MISSING, OWNERS);

		assertEquals(1, requests.size());
		assertEquals("ID35261234567800019065001000000011000000012", requests.get(0).id());
		assertEquals(VoidingRequest.STANDARD_JUSTIFICATION, requests.get(0).justification());
	}

	@Test
	void refusesANumberThatIsNotMissing() {
		String message = blocked(Map.of(1, List.of(new NumberRange(12, 13))), MISSING, OWNERS);

		assertTrue(message.contains("13"));
		assertTrue(message.contains("faltantes"));
	}

	@Test
	void refusesANumberAlreadyInTheHistory() {
		issued = Set.of(11);

		String message = blocked(Map.of(1, List.of(new NumberRange(10, 12))), MISSING, OWNERS);

		assertTrue(message.contains("11"));
		assertTrue(message.contains("histórico"));
	}

	@Test
	void refusesWhenTheHistoryCannotBeRead() {
		VoidingPlanner failing = new VoidingPlanner(new IssuedNumberRepository() {

			@Override
			public void initialize() {
			}

			@Override
			public void saveAll(Collection<IssuedNumber> numbers) {
			}

			@Override
			public Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) throws IOException {
				throw new IOException("banco travado");
			}
		});

		assertThrows(VoidingException.class, () -> failing.plan(Map.of(1, List.of(new NumberRange(10, 10))), MISSING, OWNERS));
	}

	@Test
	void refusesASeriesThatDidNotAppearInTheCollection() {
		assertTrue(blocked(Map.of(2, List.of(new NumberRange(1, 1))), Map.of(2, List.of(new NumberRange(1, 1))), OWNERS).contains("não apareceu"));
	}

	@Test
	void refusesASeriesWithMoreThanOneCnpjOrYear() {
		Map<Integer, Set<SeriesOwner>> mixed = Map.of(1, Set.of(OWNER, new SeriesOwner("35", "12345678000190", 2025)));

		assertTrue(blocked(Map.of(1, List.of(new NumberRange(10, 10))), MISSING, mixed).contains("mais de um"));
	}

	@Test
	void refusesWhenThereIsNothingToVoid() {
		assertTrue(blocked(Map.of(), MISSING, OWNERS).contains("Não há"));
	}

	@Test
	void buildsThePdvFolderOfEachSeriesWithTheCnpjAndTheMonth() throws Exception {
		VoidingRequest request = planner.plan(Map.of(1, List.of(new NumberRange(10, 10))), MISSING, OWNERS).get(0);

		Map<Integer, List<java.nio.file.Path>> folders = VoidingPlanner.pdvFolders(List.of(request), List.of("raiz0", "raiz1"),
				Map.of(1, Set.of(1)), "docs/{CNPJ}/{AAAAMM}/saida", java.time.YearMonth.of(2026, 10));

		assertEquals(List.of(java.nio.file.Path.of("raiz1", "docs", "12345678000190", "202610", "saida")), folders.get(1));
	}

	@Test
	void savesInEveryPdvOfTheSeries() throws Exception {
		VoidingRequest request = planner.plan(Map.of(1, List.of(new NumberRange(10, 10))), MISSING, OWNERS).get(0);

		assertEquals(2, VoidingPlanner.pdvFolders(List.of(request), List.of("raiz0", "raiz1"),
				Map.of(1, Set.of(0, 1)), "saida", java.time.YearMonth.of(2026, 10)).get(1).size());
	}

	@Test
	void refusesWithoutAPdvFolderToSaveTheXml() throws Exception {
		VoidingRequest request = planner.plan(Map.of(1, List.of(new NumberRange(10, 10))), MISSING, OWNERS).get(0);

		assertThrows(VoidingException.class, () -> VoidingPlanner.pdvFolders(List.of(request), List.of("raiz0"),
				Map.of(1, Set.of(0)), "", java.time.YearMonth.of(2026, 10)));
		assertThrows(VoidingException.class, () -> VoidingPlanner.pdvFolders(List.of(request), List.of("raiz0"),
				Map.of(), "saida", java.time.YearMonth.of(2026, 10)));
		assertThrows(VoidingException.class, () -> VoidingPlanner.pdvFolders(List.of(request), List.of(""),
				Map.of(1, Set.of(0)), "saida", java.time.YearMonth.of(2026, 10)));
	}

	@Test
	void anyFailureOrReadErrorCountsAsAProblem() {
		assertEquals(List.of(), VoidingPlanner.problemsOf(result(List.of(), 0)));
		assertEquals(1, VoidingPlanner.problemsOf(result(List.of(new SourceFailure(0, SourceFailure.Reason.INACCESSIBLE, "pdv")), 0)).size());
		assertEquals(1, VoidingPlanner.problemsOf(result(List.of(), 2)).size());
	}

	private String blocked(Map<Integer, List<NumberRange>> wanted, Map<Integer, List<NumberRange>> missing, Map<Integer, Set<SeriesOwner>> owners) {
		return assertThrows(VoidingException.class, () -> planner.plan(wanted, missing, owners)).getMessage();
	}

	private static CollectionResult result(List<SourceFailure> failures, int errors) {
		return new CollectionResult(List.of(), new TreeMap<>(),
				new CollectionSummary(0, 0, 0, 0, 0, 0, 0, errors, List.of()), failures, null, Set.of());
	}
}
