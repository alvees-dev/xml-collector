package com.alveesdev.xmlcollector.application.voiding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntPredicate;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;

// What is known about the missing numbers since the last collection, and what that allows to be voided.
public final class MissingNumbersSession {

	private static final int MAX_LISTED_PROBLEMS = 15;

	private CollectionRequest lastCollection;
	private SortedMap<Integer, List<NumberRange>> missingBySeries = new TreeMap<>();
	private Map<Integer, Set<Integer>> sourcesBySeries = Map.of();
	private Map<Integer, Set<SeriesOwner>> ownersBySeries = Map.of();
	private Map<Integer, Integer> firstNumberBySeries = Map.of();
	private List<String> collectionProblems = List.of();
	private boolean searched;
	private List<String> searchProblems = List.of();

	public void registerCollection(CollectionRequest request, CollectionResult result) {
		lastCollection = request;
		missingBySeries = result.missingBySeries();
		sourcesBySeries = result.sourcesBySeries();
		ownersBySeries = result.ownersBySeries();
		firstNumberBySeries = result.firstNumberBySeries();
		collectionProblems = VoidingPlanner.problemsOf(result);
		searched = false;
		searchProblems = List.of();
	}

	// Only the search of all the missing numbers counts: looking for one number says nothing about the others.
	public void registerSearchOfAll(CollectionResult result) {
		searched = true;
		searchProblems = VoidingPlanner.problemsOf(result);
	}

	public List<XmlDocument.Invoice> registerFound(CollectionResult result) {

		List<XmlDocument.Invoice> found = result.entries().stream()
				.map(DocumentEntry::invoice)
				.filter(invoice -> invoice != null)
				.toList();

		SortedMap<Integer, List<NumberRange>> remaining = new TreeMap<>();

		missingBySeries.forEach((series, ranges) -> {
			List<NumberRange> foundInSeries = new ArrayList<>();

			for (XmlDocument.Invoice invoice : found) {
				if (invoice.series() == series) {
					foundInSeries.add(new NumberRange(invoice.number(), invoice.number()));
				}
			}

			remaining.put(series, MissingNumberFinder.subtract(ranges, foundInSeries));
		});

		missingBySeries = remaining;

		return found;
	}

	// A homologation approval has no fiscal effect, so the numbers are still missing.
	public boolean registerVoided(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		if (environment != FiscalEnvironment.PRODUCTION) {
			return false;
		}

		SortedMap<Integer, List<NumberRange>> remaining = new TreeMap<>(missingBySeries);

		for (VoidingOutcome outcome : outcomes) {
			if (outcome.stage() == Stage.APPROVED) {
				int series = outcome.request().series();
				remaining.put(series, MissingNumberFinder.subtract(remaining.getOrDefault(series, List.of()), List.of(outcome.request().range())));
			}
		}

		missingBySeries = remaining;

		return true;
	}

	public Optional<String> voidingBlock(List<String> configuredPdvs, boolean searchRequired) {

		if (lastCollection == null) {
			return Optional.of("Faça a coleta antes de inutilizar.");
		}

		if (!readTheConfiguredPdvs(configuredPdvs)) {
			return Optional.of("A última coleta não leu os PDVs configurados. Colete por Diretório PDV antes de inutilizar.");
		}

		if (!collectionProblems.isEmpty()) {
			return Optional.of(blockedBy("A coleta teve problemas, e uma nota pode existir sem ter sido lida:", collectionProblems));
		}

		if (!searched) {
			return searchRequired
					? Optional.of("Clique em Procurar Todas antes de inutilizar, para só inutilizar o que realmente não foi encontrado.")
					: Optional.empty();
		}

		if (!searchProblems.isEmpty()) {
			return Optional.of(blockedBy("A busca teve problemas, e uma nota pode existir sem ter sido lida:", searchProblems));
		}

		return Optional.empty();
	}

	public boolean searchedAll() {
		return searched;
	}

	public boolean hasCollection() {
		return lastCollection != null;
	}

	public boolean nothingMissing() {
		return missingBySeries.values().stream().allMatch(List::isEmpty);
	}

	public SortedMap<Integer, List<NumberRange>> missing() {
		return missingBySeries;
	}

	public SortedMap<Integer, List<NumberRange>> missingWithNumbers() {

		SortedMap<Integer, List<NumberRange>> withNumbers = new TreeMap<>();

		missingBySeries.forEach((series, ranges) -> {
			if (!ranges.isEmpty()) {
				withNumbers.put(series, ranges);
			}
		});

		return withNumbers;
	}

	public Map<Integer, Set<Integer>> sourcesBySeries() {
		return sourcesBySeries;
	}

	public Map<Integer, Set<SeriesOwner>> ownersBySeries() {
		return ownersBySeries;
	}

	public LocalDate lastCollectedDay() {
		return lastCollection.period().to();
	}

	// A gap before the first number can be the last note of the previous month.
	public DateRange searchPeriod(Map<Integer, List<NumberRange>> wanted) {
		return startsBeforeTheCollection(wanted) ? lastCollection.period().fromThePreviousMonth() : lastCollection.period();
	}

	public IntPredicate sourcesToSearch(Map<Integer, List<NumberRange>> wanted, List<String> configuredPdvs) {

		// The series of each PDV come from the last collection, which only applies if it read these same PDVs.
		if (!readTheConfiguredPdvs(configuredPdvs)) {
			return index -> true;
		}

		Set<Integer> sources = new HashSet<>();

		wanted.forEach((series, ranges) -> {
			if (!ranges.isEmpty()) {
				sources.addAll(sourcesBySeries.getOrDefault(series, Set.of()));
			}
		});

		return sources.isEmpty() ? index -> true : sources::contains;
	}

	private boolean readTheConfiguredPdvs(List<String> configuredPdvs) {
		return lastCollection.directories().equals(configuredPdvs);
	}

	private boolean startsBeforeTheCollection(Map<Integer, List<NumberRange>> wanted) {
		return wanted.entrySet().stream().anyMatch(entry -> entry.getValue().stream()
				.anyMatch(range -> firstNumberBySeries.containsKey(entry.getKey()) && range.start() < firstNumberBySeries.get(entry.getKey())));
	}

	private static String blockedBy(String reason, List<String> problems) {
		return reason + "\n\n" + String.join("\n", problems.stream().limit(MAX_LISTED_PROBLEMS).toList())
				+ "\n\nResolva e faça a coleta de novo antes de inutilizar.";
	}
}
