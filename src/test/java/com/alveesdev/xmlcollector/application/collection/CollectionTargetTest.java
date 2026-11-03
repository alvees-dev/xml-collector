package com.alveesdev.xmlcollector.application.collection;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.NumberRange;

class CollectionTargetTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
	private static final Map<Integer, List<NumberRange>> WANTED = Map.of(
			1, List.of(new NumberRange(10, 12)), 2, List.of(new NumberRange(15, 15)));
	private static final CollectionTarget ANY_FILE = CollectionTarget.missing(WANTED, index -> true, "");
	private static final CollectionTarget NFE_FILES = CollectionTarget.missing(WANTED, index -> true, "-nfe.xml");

	@Test
	void readsTheSeriesAndTheNumberOfAKey() {
		assertEquals(3, key(3, 50).series());
		assertEquals(50, key(3, 50).number());
	}

	@Test
	void wantsOnlyTheMissingNumberOfEachSeries() {
		assertTrue(ANY_FILE.invoice().test(invoice(1, 11, DAY)));
		assertTrue(ANY_FILE.invoice().test(invoice(2, 15, DAY)));
		assertFalse(ANY_FILE.invoice().test(invoice(2, 11, DAY)));
		assertFalse(ANY_FILE.invoice().test(invoice(3, 15, DAY)));
	}

	@Test
	void decidesByTheKeyInTheFileName() {
		assertTrue(ANY_FILE.fileName().test(key(1, 11).value() + ".xml"));
		assertFalse(ANY_FILE.fileName().test(key(2, 11).value() + "-nfe.xml"));
	}

	@Test
	void opensTheFileWhenTheNameHasNoKey() {
		assertTrue(ANY_FILE.fileName().test("nota.xml"));
		assertTrue(ANY_FILE.fileName().test("110111" + key(9, 99).value() + "01-procEventoNFe.xml"));
	}

	@Test
	void acceptsOnlyTheSuffixOfTheModelEvenWithAWantedKey() {
		assertTrue(NFE_FILES.fileName().test(key(1, 11).value() + "-nfe.xml"));
		assertTrue(NFE_FILES.fileName().test(key(1, 11).value() + "-NFe.XML"));
		assertFalse(NFE_FILES.fileName().test(key(1, 11).value() + ".xml"));
		assertFalse(NFE_FILES.fileName().test(key(1, 11).value() + "-procEventoNFe.xml"));
		assertFalse(NFE_FILES.fileName().test("nota.xml"));
	}

	@Test
	void theSuffixDoesNotAcceptANumberThatIsNotWanted() {
		assertFalse(NFE_FILES.fileName().test(key(2, 11).value() + "-nfe.xml"));
	}
}
