package com.alveesdev.xmlcollector.application.voiding;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class MissingNumbersSessionTest {

	private static final List<String> PDVS = List.of("pdv1", "pdv2");
	private static final LocalDate DAY = LocalDate.of(2026, 2, 15);
	private static final DateRange FEBRUARY = new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
	private static final CollectionRequest COLLECTION = new CollectionRequest(PDVS, DocumentCategory.NFCE, FEBRUARY, OrganizeMode.SINGLE_FOLDER,
			Path.of("arquivo"), List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);
	private static final NumberRange GAP = new NumberRange(5, 7);

	private final MissingNumbersSession session = new MissingNumbersSession();

	// Each of the blocks below guards an operation that cannot be undone, and their order decides which message is shown.

	@Test
	void nothingCanBeVoidedBeforeACollection() {
		assertBlockedWith("Faça a coleta", session.voidingBlock(PDVS, false));
		assertBlockedWith("Faça a coleta", session.voidingBlock(PDVS, true));
	}

	@Test
	void aCollectionOfOtherPdvsBlocksTheVoiding() {
		session.registerCollection(COLLECTION, clean());

		assertBlockedWith("não leu os PDVs", session.voidingBlock(List.of("pdv1"), false));
	}

	@Test
	void otherPdvsAreReportedBeforeTheProblemsOfTheCollection() {
		session.registerCollection(COLLECTION, withErrors(1));

		assertBlockedWith("não leu os PDVs", session.voidingBlock(List.of("pdv1"), false));
	}

	@Test
	void aFileThatCouldNotBeReadBlocksTheVoiding() {
		session.registerCollection(COLLECTION, withErrors(2));

		Optional<String> block = session.voidingBlock(PDVS, false);

		assertBlockedWith("A coleta teve problemas", block);
		assertBlockedWith("2 arquivo(s) com erro de leitura", block);
	}

	@Test
	void aFolderThatCouldNotBeReadBlocksTheVoiding() {
		session.registerCollection(COLLECTION, withFailures(1));

		assertBlockedWith("A coleta teve problemas", session.voidingBlock(PDVS, false));
	}

	@Test
	void aFailureInTheHistoryBlocksTheVoiding() {
		session.registerCollection(COLLECTION, result(List.of(), List.of(), 0, "banco indisponível"));

		assertBlockedWith("banco indisponível", session.voidingBlock(PDVS, false));
	}

	@Test
	void theListOfProblemsIsLimited() {
		session.registerCollection(COLLECTION, withFailures(40));

		String block = session.voidingBlock(PDVS, false).orElseThrow();

		assertTrue(block.contains("pasta14"), block);
		assertFalse(block.contains("pasta15"), block);
	}

	@Test
	void voidingAllRequiresTheSearchOfAll() {
		session.registerCollection(COLLECTION, clean());

		assertBlockedWith("Procurar Todas", session.voidingBlock(PDVS, true));
	}

	@Test
	void voidingOneNumberDoesNotRequireTheSearch() {
		session.registerCollection(COLLECTION, clean());

		assertEquals(Optional.empty(), session.voidingBlock(PDVS, false));
		assertFalse(session.searchedAll());
	}

	@Test
	void problemsOfTheCollectionComeBeforeTheMissingSearch() {
		session.registerCollection(COLLECTION, withErrors(1));

		assertBlockedWith("A coleta teve problemas", session.voidingBlock(PDVS, true));
	}

	@Test
	void aCleanSearchAllowsVoidingAll() {
		session.registerCollection(COLLECTION, clean());
		session.registerSearchOfAll(clean());

		assertEquals(Optional.empty(), session.voidingBlock(PDVS, true));
		assertTrue(session.searchedAll());
	}

	@Test
	void aSearchWithProblemsBlocksEvenOneNumber() {
		session.registerCollection(COLLECTION, clean());
		session.registerSearchOfAll(withErrors(1));

		assertBlockedWith("A busca teve problemas", session.voidingBlock(PDVS, true));
		assertBlockedWith("A busca teve problemas", session.voidingBlock(PDVS, false));
	}

	@Test
	void aNewCollectionForgetsTheSearch() {
		session.registerCollection(COLLECTION, clean());
		session.registerSearchOfAll(withErrors(1));

		session.registerCollection(COLLECTION, clean());

		assertFalse(session.searchedAll());
		assertBlockedWith("Procurar Todas", session.voidingBlock(PDVS, true));
		assertEquals(Optional.empty(), session.voidingBlock(PDVS, false));
	}

	@Test
	void findingANumberDoesNotCountAsTheSearchOfAll() {
		session.registerCollection(COLLECTION, clean());

		session.registerFound(foundNotes(invoice(1, 6, DAY)));

		assertFalse(session.searchedAll());
		assertBlockedWith("Procurar Todas", session.voidingBlock(PDVS, true));
	}

	@Test
	void whatWasFoundLeavesTheList() {
		session.registerCollection(COLLECTION, clean());

		List<XmlDocument.Invoice> found = session.registerFound(foundNotes(invoice(1, 6, DAY)));

		assertEquals(List.of(invoice(1, 6, DAY)), found);
		assertEquals(List.of(new NumberRange(5, 5), new NumberRange(7, 7)), session.missing().get(1));
	}

	@Test
	void aNoteOfAnotherSeriesDoesNotChangeTheList() {
		session.registerCollection(COLLECTION, clean());

		session.registerFound(foundNotes(invoice(2, 6, DAY)));

		assertEquals(List.of(GAP), session.missing().get(1));
		assertFalse(session.missing().containsKey(2));
	}

	@Test
	void aFileThatCouldNotBeReadIsNotAFoundNote() {
		session.registerCollection(COLLECTION, clean());

		CollectionResult result = result(List.of(new SourceResult(0, List.of(DocumentEntry.error("quebrado.xml")))), List.of(), 1, null);

		assertEquals(List.of(), session.registerFound(result));
		assertEquals(List.of(GAP), session.missing().get(1));
	}

	@Test
	void anApprovedVoidingInProductionLeavesTheList() {
		session.registerCollection(COLLECTION, clean());

		boolean changed = session.registerVoided(List.of(outcome(new NumberRange(5, 6), Stage.APPROVED)), FiscalEnvironment.PRODUCTION);

		assertTrue(changed);
		assertEquals(List.of(new NumberRange(7, 7)), session.missing().get(1));
	}

	@Test
	void homologationHasNoFiscalEffectOnTheList() {
		session.registerCollection(COLLECTION, clean());

		boolean changed = session.registerVoided(List.of(outcome(GAP, Stage.APPROVED)), FiscalEnvironment.HOMOLOGATION);

		assertFalse(changed);
		assertEquals(List.of(GAP), session.missing().get(1));
	}

	@Test
	void onlyAnApprovalTakesANumberOutOfTheList() {
		session.registerCollection(COLLECTION, clean());

		session.registerVoided(List.of(outcome(GAP, Stage.REJECTED), outcome(GAP, Stage.FAILED), outcome(GAP, Stage.NOT_SENT)), FiscalEnvironment.PRODUCTION);

		assertEquals(List.of(GAP), session.missing().get(1));
	}

	@Test
	void knowsWhenThereIsACollectionAndSomethingMissing() {
		assertFalse(session.hasCollection());
		assertTrue(session.nothingMissing());

		session.registerCollection(COLLECTION, clean());

		assertTrue(session.hasCollection());
		assertFalse(session.nothingMissing());
	}

	@Test
	void onlyTheSeriesWithNumbersGoToTheVoiding() {
		TreeMap<Integer, List<NumberRange>> missing = new TreeMap<>(Map.of(1, List.of(GAP), 2, List.of()));
		session.registerCollection(COLLECTION, new CollectionResult(List.of(), missing, summary(0), List.of(), null, Set.of()));

		assertEquals(Map.of(1, List.of(GAP)), session.missingWithNumbers());
		assertEquals(Set.of(1, 2), session.missing().keySet());
	}

	@Test
	void theDataOfTheCollectionIsKept() {
		session.registerCollection(COLLECTION, clean());

		assertEquals(FEBRUARY.to(), session.lastCollectedDay());
		assertEquals(Map.of(1, Set.of(0)), session.sourcesBySeries());
		assertEquals(Map.of(1, Set.of(new SeriesOwner("35", "12345678000190", 2026))), session.ownersBySeries());
	}

	@Test
	void aNumberAfterTheFirstIsSearchedInThePeriodOfTheCollection() {
		session.registerCollection(COLLECTION, clean());

		assertEquals(FEBRUARY, session.searchPeriod(Map.of(1, List.of(GAP))));
	}

	@Test
	void aNumberBeforeTheFirstIsAlsoSearchedInThePreviousMonth() {
		session.registerCollection(COLLECTION, clean());

		assertEquals(new DateRange(LocalDate.of(2026, 1, 1), FEBRUARY.to()), session.searchPeriod(Map.of(1, List.of(new NumberRange(2, 3)))));
	}

	@Test
	void aSeriesOutOfTheCollectionKeepsItsPeriod() {
		session.registerCollection(COLLECTION, clean());

		assertEquals(FEBRUARY, session.searchPeriod(Map.of(9, List.of(new NumberRange(1, 1)))));
	}

	@Test
	void theSearchGoesOnlyToThePdvOfTheSeries() {
		session.registerCollection(COLLECTION, clean());

		IntPredicate sources = session.sourcesToSearch(Map.of(1, List.of(GAP)), PDVS);

		assertTrue(sources.test(0));
		assertFalse(sources.test(1));
	}

	@Test
	void otherPdvsAreAllSearchedBecauseTheirSeriesAreUnknown() {
		session.registerCollection(COLLECTION, clean());

		IntPredicate sources = session.sourcesToSearch(Map.of(1, List.of(GAP)), List.of("outro"));

		assertTrue(sources.test(0));
		assertTrue(sources.test(1));
	}

	@Test
	void aSeriesWithoutAKnownPdvSearchesAllOfThem() {
		session.registerCollection(COLLECTION, clean());

		IntPredicate sources = session.sourcesToSearch(Map.of(9, List.of(GAP)), PDVS);

		assertTrue(sources.test(0));
		assertTrue(sources.test(1));
	}

	@Test
	void aSeriesWithoutNumbersDoesNotChooseThePdv() {
		session.registerCollection(COLLECTION, clean());

		IntPredicate sources = session.sourcesToSearch(Map.of(1, List.of()), PDVS);

		assertTrue(sources.test(1));
	}

	private static void assertBlockedWith(String text, Optional<String> block) {
		assertTrue(block.isPresent());
		assertTrue(block.get().contains(text), block.get());
	}

	private static VoidingOutcome outcome(NumberRange range, Stage stage) {
		return new VoidingOutcome(new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, 1, range, VoidingRequest.STANDARD_JUSTIFICATION), stage, null);
	}

	private static CollectionResult clean() {
		return result(notesOfTheFirstPdv(), List.of(), 0, null);
	}

	private static CollectionResult withErrors(int errors) {
		return result(notesOfTheFirstPdv(), List.of(), errors, null);
	}

	private static CollectionResult withFailures(int count) {
		List<SourceFailure> failures = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			failures.add(new SourceFailure(0, SourceFailure.Reason.INACCESSIBLE, "pasta" + index));
		}

		return result(notesOfTheFirstPdv(), failures, 0, null);
	}

	private static CollectionResult foundNotes(XmlDocument.Invoice... invoices) {
		return result(List.of(new SourceResult(0, List.of(invoices).stream().map(invoice -> DocumentEntry.of(invoice, false, "nota.xml")).toList())), List.of(), 0, null);
	}

	private static List<SourceResult> notesOfTheFirstPdv() {
		return List.of(new SourceResult(0, List.of(DocumentEntry.of(invoice(1, 4, DAY), false, "4.xml"), DocumentEntry.of(invoice(1, 8, DAY), false, "8.xml"))));
	}

	private static CollectionResult result(List<SourceResult> sources, List<SourceFailure> failures, int errors, String historyFailure) {
		return new CollectionResult(sources, new TreeMap<>(Map.of(1, List.of(GAP))), summary(errors), failures, historyFailure, Set.of());
	}

	private static CollectionSummary summary(int errors) {
		return new CollectionSummary(0, 0, 0, 0, 0, 0, 0, errors, List.of());
	}
}
