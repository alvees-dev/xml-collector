package com.alveesdev.xmlcollector.application.collection;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class CollectionResultTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
	private static final SeriesOwner OWNER = new SeriesOwner("35", "12345678000190", 2026);

	@Test
	void theOwnerOfASeriesComesFromTheKeysOfItsNotes() {
		CollectionResult result = resultOf(
				new SourceResult(0, List.of(authorized(invoice(1, 10, DAY)), authorized(invoice(1, 11, DAY)))),
				new SourceResult(1, List.of(authorized(invoice(2, 5, DAY)))));

		assertEquals(Map.of(1, Set.of(OWNER), 2, Set.of(OWNER)), result.ownersBySeries());
	}

	@Test
	void aSeriesWithNotesOfAnotherCnpjHasBothOwners() {
		XmlDocument.Invoice original = invoice(1, 10, DAY);
		String key = original.key().value();
		AccessKey ofOtherCompany = new AccessKey(key.substring(0, 6) + "98765432000110" + key.substring(20));

		CollectionResult result = resultOf(new SourceResult(0, List.of(authorized(original),
				authorized(new XmlDocument.Invoice(ofOtherCompany, 1, 11, DAY.atStartOfDay().atOffset(ZoneOffset.UTC), BigDecimal.ONE)))));

		assertEquals(Map.of(1, Set.of(OWNER, new SeriesOwner("35", "98765432000110", 2026))), result.ownersBySeries());
	}

	@Test
	void aFileThatCouldNotBeReadHasNoOwner() {
		CollectionResult result = resultOf(new SourceResult(0, List.of(DocumentEntry.error("quebrado.xml"))));

		assertEquals(Map.of(), result.ownersBySeries());
	}

	private static DocumentEntry authorized(XmlDocument.Invoice invoice) {
		return DocumentEntry.of(invoice, false, invoice.number() + ".xml");
	}

	private static CollectionResult resultOf(SourceResult... sources) {
		return new CollectionResult(List.of(sources), new TreeMap<>(), new CollectionSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()),
				List.of(), null, Set.of());
	}
}
