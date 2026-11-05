package com.alveesdev.xmlcollector.domain.service;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.CollectionRules.Decision;
import com.alveesdev.xmlcollector.domain.service.CollectionRules.Skip;
import com.alveesdev.xmlcollector.domain.service.CollectionRules.SkipReason;
import com.alveesdev.xmlcollector.domain.service.CollectionRules.Store;

class CollectionRulesTest {

	private static final LocalDate FIRST_DAY = LocalDate.of(2026, 1, 1);
	private static final LocalDate LAST_DAY = LocalDate.of(2026, 1, 31);
	private static final LocalDate IN_PERIOD = LocalDate.of(2026, 1, 15);
	private static final LocalDate BEFORE_PERIOD = FIRST_DAY.minusDays(1);
	private static final LocalDate AFTER_PERIOD = LAST_DAY.plusDays(1);

	private final CollectionRules rules = new CollectionRules(new DateRange(FIRST_DAY, LAST_DAY), invoice -> true);

	@Test
	void storesAnInvoiceInThePeriodKeepingItsFileName() {
		XmlDocument.Invoice invoice = invoice(1, IN_PERIOD);
		String key = invoice.key().value();

		assertEquals(new Store(IN_PERIOD, "nota.xml", key + ".xml", key, false), rules.decide(0, "nota.xml", invoice));
		assertEquals(DocumentStatus.AUTHORIZED, onlyEntry(0).status());
	}

	@Test
	void includesBothEndsOfThePeriod() {
		assertInstanceOf(Store.class, rules.decide(0, "a.xml", invoice(1, FIRST_DAY)));
		assertInstanceOf(Store.class, rules.decide(0, "b.xml", invoice(2, LAST_DAY)));
	}

	@Test
	void skipsAnInvoiceOutsideThePeriodWithoutListingIt() {
		assertSkipped(SkipReason.OUT_OF_PERIOD, rules.decide(0, "a.xml", invoice(1, BEFORE_PERIOD)));
		assertSkipped(SkipReason.OUT_OF_PERIOD, rules.decide(0, "b.xml", invoice(2, AFTER_PERIOD)));
		assertTrue(rules.entriesOf(0).isEmpty());
	}

	@Test
	void skipsARepeatedInvoiceEvenInAnotherSource() {
		rules.decide(0, "a.xml", invoice(1, IN_PERIOD));

		assertSkipped(SkipReason.DUPLICATE, rules.decide(0, "copia.xml", invoice(1, IN_PERIOD)));
		assertSkipped(SkipReason.DUPLICATE, rules.decide(1, "a.xml", invoice(1, IN_PERIOD)));
		assertTrue(rules.entriesOf(1).isEmpty());
	}

	@Test
	void treatsARepeatedOutOfPeriodInvoiceAsDuplicate() {
		rules.decide(0, "a.xml", invoice(1, AFTER_PERIOD));

		assertSkipped(SkipReason.DUPLICATE, rules.decide(0, "b.xml", invoice(1, AFTER_PERIOD)));
	}

	@Test
	void storesACancellationUnderItsEventId() {
		XmlDocument.CancelEvent event = cancellation(1, IN_PERIOD);
		String name = event.eventId() + ".xml";

		assertEquals(new Store(IN_PERIOD, name, name, event.eventId(), false), rules.decide(0, "evento.xml", event));
	}

	@Test
	void aVoidingAfterThePeriodStillClosesTheGapButIsNotStored() {
		rules.decide(0, "a.xml", invoice(1, IN_PERIOD));
		rules.decide(0, "b.xml", invoice(4, IN_PERIOD));

		XmlDocument.Voiding voiding = new XmlDocument.Voiding(
				"ID1", "12345678000190", "65", 1, new NumberRange(2, 2), AFTER_PERIOD.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(-3)));

		assertSkipped(SkipReason.OUT_OF_PERIOD, rules.decide(0, "inut.xml", voiding));
		assertEquals(java.util.Map.of(1, java.util.List.of(new NumberRange(3, 3))), rules.missingNumbers(Map.of()));
	}

	@Test
	void aVoidingOutsideTheCollectedNumbersDoesNotCreateAGap() {
		rules.decide(0, "a.xml", invoice(1, IN_PERIOD));
		rules.decide(0, "b.xml", invoice(2, IN_PERIOD));

		XmlDocument.Voiding voiding = new XmlDocument.Voiding(
				"ID1", "12345678000190", "65", 1, new NumberRange(500, 501), IN_PERIOD.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(-3)));

		assertTrue(rules.decide(0, "inut.xml", voiding) instanceof Store store && store.voiding());
		assertEquals(java.util.Map.of(1, java.util.List.<NumberRange>of()), rules.missingNumbers(Map.of()));
	}

	@Test
	void skipsARepeatedCancellation() {
		rules.decide(0, "evento.xml", cancellation(1, IN_PERIOD));

		assertSkipped(SkipReason.DUPLICATE, rules.decide(1, "evento.xml", cancellation(1, IN_PERIOD)));
	}

	@Test
	void cancelsAnInvoiceWhateverTheOrderOrSource() {
		rules.decide(0, "evento1.xml", cancellation(1, IN_PERIOD));
		rules.decide(0, "nota1.xml", invoice(1, IN_PERIOD));
		rules.decide(0, "nota2.xml", invoice(2, IN_PERIOD));
		rules.decide(1, "evento2.xml", cancellation(2, IN_PERIOD));
		rules.decide(1, "evento3.xml", cancellation(3, IN_PERIOD));
		rules.decide(2, "nota3.xml", invoice(3, IN_PERIOD));

		assertEquals(
				List.of(DocumentStatus.CANCELLED, DocumentStatus.CANCELLED),
				rules.entriesOf(0).stream().map(DocumentEntry::status).toList());
		assertEquals(DocumentStatus.CANCELLED, onlyEntry(2).status());
	}

	@Test
	void anOutOfPeriodCancellationIsNotStoredButStillCancelsTheInvoice() {
		rules.decide(0, "nota.xml", invoice(1, IN_PERIOD));

		assertSkipped(SkipReason.OUT_OF_PERIOD, rules.decide(0, "evento.xml", cancellation(1, AFTER_PERIOD)));
		assertEquals(DocumentStatus.CANCELLED, onlyEntry(0).status());
	}

	@Test
	void anUncancelledInvoiceStaysAuthorized() {
		rules.decide(0, "nota.xml", invoice(1, IN_PERIOD));
		rules.decide(0, "evento.xml", cancellation(2, IN_PERIOD));

		assertEquals(DocumentStatus.AUTHORIZED, onlyEntry(0).status());
	}

	@Test
	void skipsAnIgnoredDocumentWithoutListingIt() {
		assertSkipped(SkipReason.IGNORED, rules.decide(0, "nfe.xml", new XmlDocument.Ignored("model 55")));
		assertTrue(rules.entriesOf(0).isEmpty());
	}

	@Test
	void listsAnUnreadableFileAsAnError() {
		assertSkipped(SkipReason.UNREADABLE, rules.decide(0, "ruim.xml", new XmlDocument.Unreadable("broken")));

		DocumentEntry entry = onlyEntry(0);

		assertEquals(DocumentStatus.ERROR, entry.status());
		assertEquals("ruim.xml", entry.fileName());
		assertNull(entry.invoice());
	}

	@Test
	void aNoteOutOfThePeriodExistsAndAnUnreadableOneIsMissing() {
		rules.decide(0, "a.xml", invoice(1, IN_PERIOD));
		rules.decide(0, "fora.xml", invoice(2, AFTER_PERIOD));
		rules.decide(0, "ruim.xml", new XmlDocument.Unreadable("broken"));
		rules.decide(0, "b.xml", invoice(4, IN_PERIOD));

		assertEquals(Map.of(1, List.of(new NumberRange(3, 3))), rules.missingNumbers(Map.of()));
	}

	@Test
	void anInvoiceInTheWrongSourceStillFillsItsSeries() {
		rules.decide(0, "a.xml", invoice(1, 1, IN_PERIOD));
		rules.decide(0, "c.xml", invoice(1, 3, IN_PERIOD));
		rules.decide(1, "b.xml", invoice(1, 2, IN_PERIOD));

		assertEquals(Map.of(1, List.of()), rules.missingNumbers(Map.of()));
	}

	@Test
	void aCopyInAnotherSourceDoesNotCreateAGapWhicheverIsReadFirst() {
		rules.decide(0, "copia.xml", invoice(2, 2, IN_PERIOD));
		rules.decide(1, "a.xml", invoice(2, 1, IN_PERIOD));
		rules.decide(1, "b.xml", invoice(2, 2, IN_PERIOD));
		rules.decide(1, "c.xml", invoice(2, 3, IN_PERIOD));

		assertEquals(Map.of(2, List.of()), rules.missingNumbers(Map.of()));
	}

	@Test
	void theSameNumberInAnotherSeriesDoesNotFillAGap() {
		rules.decide(0, "a.xml", invoice(1, 1, IN_PERIOD));
		rules.decide(0, "c.xml", invoice(1, 3, IN_PERIOD));
		rules.decide(1, "d.xml", invoice(2, 1, IN_PERIOD));
		assertInstanceOf(Store.class, rules.decide(1, "e.xml", invoice(2, 2, IN_PERIOD)));
		rules.decide(1, "f.xml", invoice(2, 3, IN_PERIOD));

		assertEquals(Map.of(1, List.of(new NumberRange(2, 2)), 2, List.of()), rules.missingNumbers(Map.of()));
	}

	@Test
	void aSourceWithoutFilesHasNoResults() {
		assertTrue(rules.entriesOf(5).isEmpty());
		assertTrue(rules.missingNumbers(Map.of()).isEmpty());
	}

	private DocumentEntry onlyEntry(int sourceIndex) {
		List<DocumentEntry> entries = rules.entriesOf(sourceIndex);
		assertEquals(1, entries.size());
		return entries.get(0);
	}

	private static void assertSkipped(SkipReason reason, Decision decision) {
		assertEquals(new Skip(reason), decision);
	}
}
