package com.alveesdev.xmlcollector.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class TestDocuments {

	private static final String KEY_PREFIX = "35260112345678000190" + AccessKey.NFCE_MODEL;
	private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);

	private TestDocuments() {
	}

	public static AccessKey key(int number) {
		return key(1, number);
	}

	public static AccessKey key(int series, int number) {
		return new AccessKey(KEY_PREFIX + String.format("%03d%09d1%09d", series, number, number));
	}

	public static XmlDocument.Nfce invoice(int number, LocalDate date) {
		return invoice(1, number, date);
	}

	public static XmlDocument.Nfce invoice(int series, int number, LocalDate date) {
		return new XmlDocument.Nfce(key(series, number), series, number, atNoon(date), new BigDecimal("10.50"));
	}

	public static XmlDocument.CancelEvent cancellation(int number, LocalDate date) {
		AccessKey key = key(number);
		return new XmlDocument.CancelEvent("110111" + key.value() + "01", key, atNoon(date));
	}

	private static OffsetDateTime atNoon(LocalDate date) {
		return date.atTime(12, 0).atOffset(BRASILIA);
	}
}
