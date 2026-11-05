package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DateRangeTest {

	@Test
	void refusesADateThatWasNotInformed() {
		LocalDate day = LocalDate.of(2026, 1, 15);

		assertThrows(NullPointerException.class, () -> new DateRange(null, day));
		assertThrows(NullPointerException.class, () -> new DateRange(day, null));
	}

	@Test
	void refusesAStartAfterTheEnd() {
		assertThrows(IllegalArgumentException.class, () -> new DateRange(LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 15)));
	}

	@Test
	void containsBothEndsAndNothingOutsideThem() {
		DateRange range = new DateRange(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));

		assertTrue(range.contains(LocalDate.of(2026, 1, 10)));
		assertTrue(range.contains(LocalDate.of(2026, 1, 20)));
		assertFalse(range.contains(LocalDate.of(2026, 1, 9)));
		assertFalse(range.contains(LocalDate.of(2026, 1, 21)));
	}

	@Test
	void aSingleDayIsAValidRange() {
		LocalDate day = LocalDate.of(2026, 1, 15);

		assertTrue(new DateRange(day, day).contains(day));
	}

	@Test
	void thePreviousMonthStartsOnItsFirstDayAndKeepsTheEnd() {
		DateRange range = new DateRange(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 5));

		assertEquals(new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 5)), range.fromThePreviousMonth());
	}

	@Test
	void thePreviousMonthOfJanuaryIsDecemberOfThePreviousYear() {
		DateRange range = new DateRange(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));

		assertEquals(new DateRange(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 20)), range.fromThePreviousMonth());
	}
}
