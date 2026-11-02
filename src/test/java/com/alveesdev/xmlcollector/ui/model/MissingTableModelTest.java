package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

class MissingTableModelTest {

	@Test
	void showsOneColumnPerSeriesWithSingleNumbersAndRanges() {
		TreeMap<Integer, List<NumberRange>> missing = new TreeMap<>();
		missing.put(2, List.of(new NumberRange(7, 7)));
		missing.put(1, List.of(new NumberRange(2, 2), new NumberRange(4, 6)));

		MissingTableModel model = MissingTableModel.fromResult(resultWith(missing));

		assertEquals(List.of("SERIE 1", "SERIE 2"), model.getHeaders());
		assertEquals(List.of(List.of("2", "7"), List.of("4 a 6", "")), model.getRows());
		assertTrue(model.isSeriesBased());
	}

	@Test
	void keepsTheColumnOfASeriesWithoutGaps() {
		TreeMap<Integer, List<NumberRange>> missing = new TreeMap<>();
		missing.put(1, List.of());

		MissingTableModel model = MissingTableModel.fromResult(resultWith(missing));

		assertEquals(List.of("SERIE 1"), model.getHeaders());
		assertTrue(model.getRows().isEmpty());
	}

	@Test
	void showsOneEmptyColumnPerPdvBeforeAnyCollection() {
		MissingTableModel model = MissingTableModel.forPdvs(2);

		assertEquals(List.of("PDV01", "PDV02"), model.getHeaders());
		assertTrue(model.getRows().isEmpty());
		assertFalse(model.isSeriesBased());
	}

	private static CollectionResult resultWith(TreeMap<Integer, List<NumberRange>> missing) {
		CollectionSummary summary = new CollectionSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of());
		return new CollectionResult(List.of(), missing, summary, List.of());
	}
}
