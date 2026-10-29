package com.alveesdev.xmlcollector.service;

import java.time.LocalDate;

import com.alveesdev.xmlcollector.model.NfceFileData;

public class PeriodFilter {

	public static boolean isOutsidePeriod(NfceFileData data, LocalDate from, LocalDate to) {

		if (data == null || data.documentDate() == null) {
			return false;
		}

		LocalDate date = data.documentDate();

		return date.isBefore(from) || date.isAfter(to);
	}
}