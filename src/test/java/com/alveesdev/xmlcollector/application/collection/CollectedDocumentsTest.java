package com.alveesdev.xmlcollector.application.collection;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;

class CollectedDocumentsTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

	@Test
	void aFoundInvoiceJoinsTheListAndTheTotal() {
		CollectedDocuments collected = CollectedDocuments.from(result(Set.of(), authorized(1)));

		collected.addFound(result(Set.of(), authorized(2)));

		assertEquals(2, collected.entries().size());
		assertEquals(new BigDecimal("21.00"), collected.authorizedTotal());
	}

	@Test
	void anInvoiceFoundAgainIsNotCountedTwice() {
		CollectedDocuments collected = CollectedDocuments.from(result(Set.of(), authorized(1)));

		collected.addFound(result(Set.of(), authorized(1)));

		assertEquals(1, collected.entries().size());
		assertEquals(new BigDecimal("10.50"), collected.authorizedTotal());
	}

	@Test
	void aFoundInvoiceCancelledByAnEventOfTheCollectionIsNotSummed() {
		CollectedDocuments collected = CollectedDocuments.from(result(Set.of(key(2)), authorized(1)));

		collected.addFound(result(Set.of(), authorized(2)));

		assertEquals(DocumentStatus.CANCELLED, collected.entries().get(1).status());
		assertEquals(new BigDecimal("10.50"), collected.authorizedTotal());
	}

	@Test
	void anEventFoundLaterCancelsAnInvoiceOfTheCollection() {
		CollectedDocuments collected = CollectedDocuments.from(result(Set.of(), authorized(1), authorized(2)));

		collected.addFound(result(Set.of(key(1))));

		assertEquals(DocumentStatus.CANCELLED, collected.entries().get(0).status());
		assertEquals(new BigDecimal("10.50"), collected.authorizedTotal());
	}

	@Test
	void keepsReadErrorsOfTheCollectionButNotOfTheSearch() {
		CollectedDocuments collected = CollectedDocuments.from(result(Set.of(), DocumentEntry.error("coleta.xml")));

		collected.addFound(result(Set.of(), DocumentEntry.error("busca.xml")));

		assertEquals(List.of("coleta.xml"), collected.entries().stream().map(DocumentEntry::fileName).toList());
		assertEquals(BigDecimal.ZERO, collected.authorizedTotal());
	}

	private static DocumentEntry authorized(int number) {
		return DocumentEntry.of(invoice(number, DAY), false, number + ".xml");
	}

	private static CollectionResult result(Set<AccessKey> cancelled, DocumentEntry... entries) {
		return new CollectionResult(
				List.of(new CollectionResult.SourceResult(0, List.of(entries))),
				new TreeMap<>(),
				new CollectionSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()),
				List.of(),
				null,
				cancelled);
	}
}
