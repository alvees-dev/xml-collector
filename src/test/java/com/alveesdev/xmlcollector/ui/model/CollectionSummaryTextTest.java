package com.alveesdev.xmlcollector.ui.model;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.SourceFailure.Reason;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class CollectionSummaryTextTest {

	private static final Path ARCHIVE = Path.of("arquivo");
	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

	@Test
	void showsTheCountsOfACollection() {
		String text = CollectionSummaryText.collection(result(new CollectionSummary(10, 0, 0, 3, 0, 2, 0, 1, List.of()), List.of(), null), ARCHIVE, SourceMode.PDV_DIRECTORIES);

		assertEquals(String.join("\n",
				"Copiados: 10",
				"Já existiam no destino: 3",
				"Fora do período: 2",
				"Com erro de leitura: 1",
				"",
				"Arquivos salvos em: arquivo"), text);
	}

	@Test
	void showsRenamedAndReplacedOnlyWhenTheyHappened() {
		String text = CollectionSummaryText.collection(result(new CollectionSummary(10, 2, 4, 0, 0, 0, 0, 0, List.of()), List.of(), null), ARCHIVE, SourceMode.PDV_DIRECTORIES);

		assertTrue(text.contains("Renomeados pela chave (nome repetido): 2"), text);
		assertTrue(text.contains("Substituídos (arquivo danificado no destino): 4"), text);
	}

	@Test
	void describesEachKindOfFailure() {
		List<SourceFailure> failures = List.of(
				new SourceFailure(0, Reason.NOT_INFORMED, ""),
				new SourceFailure(0, Reason.INACCESSIBLE, "a"),
				new SourceFailure(0, Reason.NO_MODEL_FOLDER, "b"),
				new SourceFailure(0, Reason.FOLDER_NOT_FOUND, "c"),
				new SourceFailure(0, Reason.SUBFOLDER_INACCESSIBLE, "d"));

		String text = CollectionSummaryText.collection(result(summary(0, List.of()), failures, null), ARCHIVE, SourceMode.PDV_DIRECTORIES);

		assertTrue(text.contains("\n\nFalhas:\n"), text);
		assertTrue(text.contains("diretório não informado"), text);
		assertTrue(text.contains("diretório inacessível (a)"), text);
		assertTrue(text.contains("nenhuma pasta do modelo de busca encontrada (b)"), text);
		assertTrue(text.contains("pasta não encontrada (c)"), text);
		assertTrue(text.contains("subpasta inacessível (d)"), text);
	}

	@Test
	void showsTheFailureOfTheHistoryAndOfTheCopies() {
		String text = CollectionSummaryText.collection(
				result(summary(0, List.of(new CollectionSummary.CopyFailure("a.xml", "disco cheio"))), List.of(), "banco indisponível"), ARCHIVE, SourceMode.PDV_DIRECTORIES);

		assertTrue(text.contains("banco indisponível"), text);
		assertTrue(text.contains("Falha ao copiar a.xml: disco cheio"), text);
	}

	@Test
	void limitsTheListOfFailures() {
		List<SourceFailure> failures = new ArrayList<>();

		for (int index = 0; index < 20; index++) {
			failures.add(new SourceFailure(0, Reason.INACCESSIBLE, "pasta" + index));
		}

		String text = CollectionSummaryText.collection(result(summary(0, List.of()), failures, null), ARCHIVE, SourceMode.PDV_DIRECTORIES);

		assertTrue(text.contains("pasta14"), text);
		assertFalse(text.contains("pasta15"), text);
		assertTrue(text.contains("... e mais 5 falha(s)"), text);
	}

	@Test
	void aCollectionHasProblemsWithFailuresOrUnreadableFiles() {
		assertFalse(CollectionSummaryText.collectionHasProblems(result(summary(0, List.of()), List.of(), null), SourceMode.PDV_DIRECTORIES));
		assertTrue(CollectionSummaryText.collectionHasProblems(result(summary(1, List.of()), List.of(), null), SourceMode.PDV_DIRECTORIES));
		assertTrue(CollectionSummaryText.collectionHasProblems(result(summary(0, List.of()), List.of(), "falhou"), SourceMode.PDV_DIRECTORIES));
	}

	@Test
	void listsTheNotesFoundInOrder() {
		List<XmlDocument.Invoice> found = List.of(invoice(2, 1, DAY), invoice(1, 9, DAY), invoice(1, 3, DAY));

		String text = CollectionSummaryText.missingSearch(result(summary(1, List.of()), List.of(), null), found, ARCHIVE);

		assertEquals(String.join("\n",
				"Encontradas: 3",
				"Série 1: 3",
				"Série 1: 9",
				"Série 2: 1",
				"",
				"Com erro de leitura: 1",
				"Destino: arquivo"), text);
	}

	@Test
	void limitsTheNotesFound() {
		List<XmlDocument.Invoice> found = new ArrayList<>();

		for (int number = 1; number <= 18; number++) {
			found.add(invoice(1, number, DAY));
		}

		String text = CollectionSummaryText.missingSearch(result(summary(0, List.of()), List.of(), null), found, ARCHIVE);

		assertTrue(text.contains("Série 1: 15"), text);
		assertFalse(text.contains("Série 1: 16"), text);
		assertTrue(text.contains("... e mais 3"), text);
	}

	// An unreadable file is already in the count of the search, which only warns about failures.
	@Test
	void aSearchHasProblemsOnlyWithFailures() {
		assertFalse(CollectionSummaryText.missingSearchHasProblems(result(summary(1, List.of()), List.of(), null)));
		assertTrue(CollectionSummaryText.missingSearchHasProblems(result(summary(0, List.of()), List.of(new SourceFailure(0, Reason.INACCESSIBLE, "a")), null)));
	}

	private static CollectionSummary summary(int errors, List<CollectionSummary.CopyFailure> copyFailures) {
		return new CollectionSummary(0, 0, 0, 0, 0, 0, 0, errors, copyFailures);
	}

	private static CollectionResult result(CollectionSummary summary, List<SourceFailure> failures, String historyFailure) {
		return new CollectionResult(List.of(), new TreeMap<>(), summary, failures, historyFailure, Set.of());
	}
}
