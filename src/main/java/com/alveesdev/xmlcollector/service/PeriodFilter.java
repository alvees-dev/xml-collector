package com.alveesdev.xmlcollector.service;

import java.time.LocalDate;
import java.time.YearMonth;

import com.alveesdev.xmlcollector.model.NfceFileData;

public class PeriodFilter {

	private static final int ACCESS_KEY_LENGTH = 44;
	private static final int YEAR_START = 2;
	private static final int MONTH_START = 4;
	private static final int MONTH_END = 6;

	public static boolean isInPeriod(String fileName, NfceFileData data, LocalDate from, LocalDate to) {

		if (data != null && data.documentDate() != null) {
			LocalDate date = data.documentDate();
			return !date.isBefore(from) && !date.isAfter(to);
		}

		YearMonth keyMonth = monthFromAccessKey(fileName);

		if (keyMonth == null) {
			return true;
		}

		return !keyMonth.isBefore(YearMonth.from(from)) && !keyMonth.isAfter(YearMonth.from(to));
	}

	private static YearMonth monthFromAccessKey(String fileName) {
		String key = fileName.replaceFirst("(?i)\\.xml$", "").replaceFirst("(?i)-nfe$", "");

		if (key.length() != ACCESS_KEY_LENGTH || !key.chars().allMatch(Character::isDigit)) {
			return null;
		}

		int year = 2000 + Integer.parseInt(key.substring(YEAR_START, MONTH_START));
		int month = Integer.parseInt(key.substring(MONTH_START, MONTH_END));

		if (month < 1 || month > 12) {
			return null;
		}

		return YearMonth.of(year, month);
	}
}