package com.alveesdev.xmlcollector.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record DateRange(LocalDate from, LocalDate to) {

	public DateRange {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);

		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Start date is after end date");
		}
	}

	public boolean contains(LocalDate date) {
		return !date.isBefore(from) && !date.isAfter(to);
	}
}