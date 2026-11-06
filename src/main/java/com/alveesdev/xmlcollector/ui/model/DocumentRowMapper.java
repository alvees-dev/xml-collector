package com.alveesdev.xmlcollector.ui.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.stream.IntStream;

import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class DocumentRowMapper {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy    HH:mm");
	private static final String UNKNOWN = "-";

	private DocumentRowMapper() {
	}

	public static DocumentRow toRow(DocumentEntry entry) {

		String status = statusLabel(entry.status());

		if (entry.invoice() == null) {
			return new DocumentRow(entry, "", "", "", "", entry.fileName(), status);
		}

		XmlDocument.Invoice invoice = entry.invoice();

		return new DocumentRow(
				entry,
				String.valueOf(invoice.number()),
				String.valueOf(invoice.series()),
				currency(invoice.totalValue()),
				DATE_FORMAT.format(invoice.issuedAt()),
				invoice.key().value(),
				status);
	}

	// One row for each number, because the list shows notes one by one; only the series and the number are known.
	public static List<DocumentRow> voidedRows(SortedMap<Integer, List<NumberRange>> voidedBySeries) {
		List<DocumentRow> rows = new ArrayList<>();

		voidedBySeries.forEach((series, ranges) -> ranges.stream()
				.flatMapToInt(range -> IntStream.rangeClosed(range.start(), range.end()))
				.distinct()
				.forEach(number -> rows.add(new DocumentRow(DocumentEntry.voided(), String.valueOf(number), String.valueOf(series),
						UNKNOWN, UNKNOWN, UNKNOWN, statusLabel(DocumentStatus.VOIDED)))));

		return rows;
	}

	// By series and number, so a voided number sits among the notes around it; a file that could not be read has neither and goes last.
	public static final Comparator<DocumentRow> BY_SERIES_AND_NUMBER = Comparator
			.comparingLong((DocumentRow row) -> numberOrLast(row.getSeries()))
			.thenComparingLong(row -> numberOrLast(row.getNumber()));

	private static long numberOrLast(String text) {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	public static String currency(BigDecimal value) {
		return NumberFormat.getCurrencyInstance(BRAZIL).format(value);
	}

	private static String statusLabel(DocumentStatus status) {
		return switch (status) {
			case AUTHORIZED -> "Autorizada";
			case CANCELLED -> "Cancelada";
			case ERROR -> "Erro na Leitura";
			case VOIDED -> "Inutilizada";
		};
	}
}