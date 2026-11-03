package com.alveesdev.xmlcollector.domain.model;

import java.util.Objects;

public record VoidingRequest(
		String ufCode,
		int year,
		String cnpj,
		DocumentModel model,
		int series,
		NumberRange range,
		String justification
) {

	private static final int MIN_YEAR = 2000;
	private static final int MAX_YEAR = 2099;
	private static final int YEARS_IN_CENTURY = 100;
	private static final int MAX_SERIES = 999;
	private static final int MAX_NUMBER = 999_999_999;
	private static final int MIN_JUSTIFICATION = 15;
	private static final int MAX_JUSTIFICATION = 255;

	public VoidingRequest {
		Objects.requireNonNull(model);
		Objects.requireNonNull(range);

		if (ufCode == null || !ufCode.matches("[0-9]{2}")) {
			throw new IllegalArgumentException("Invalid UF code: " + ufCode);
		}

		if (year < MIN_YEAR || year > MAX_YEAR) {
			throw new IllegalArgumentException("Invalid year: " + year);
		}

		if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}[0-9]{2}")) {
			throw new IllegalArgumentException("Invalid CNPJ: " + cnpj);
		}

		if (series < 0 || series > MAX_SERIES) {
			throw new IllegalArgumentException("Invalid series: " + series);
		}

		if (range.start() < 1 || range.end() > MAX_NUMBER) {
			throw new IllegalArgumentException("Invalid number range: " + range);
		}

		justification = justification == null ? "" : justification.strip();

		if (justification.length() < MIN_JUSTIFICATION || justification.length() > MAX_JUSTIFICATION) {
			throw new IllegalArgumentException("Justification must have 15 to 255 characters");
		}
	}

	public String shortYear() {
		return String.format("%02d", year % YEARS_IN_CENTURY);
	}

	public String id() {
		return String.format("ID%s%s%s%s%03d%09d%09d",
				ufCode, shortYear(), cnpj, model.code(), series, range.start(), range.end());
	}
}
