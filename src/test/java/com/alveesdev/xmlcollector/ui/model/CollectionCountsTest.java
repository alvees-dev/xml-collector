package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CollectionCountsTest {

	private final CollectionCounts counts = new CollectionCounts(
			"Coleta concluída", 6612, 14, 3, 2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	@Test
	void theNumbersHaveTheThousandsSeparatedByAPoint() {
		assertEquals("6.612 XML", counts.xmlText());
	}

	@Test
	void eachCountSaysWhatItCounts() {
		assertEquals("Coleta concluída:", counts.actionText());
		assertEquals("Canceladas: 14", counts.cancelledText());
		assertEquals("Inutilizadas: 3", counts.voidedText());
		assertEquals("Com erro: 2", counts.errorsText());
	}

	@Test
	void thePeriodIsTheOneOfTheDateFilter() {
		assertEquals("01/10/2026 a 31/10/2026", counts.periodText());
	}
}
