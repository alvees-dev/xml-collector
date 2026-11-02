package com.alveesdev.xmlcollector.ui.model;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class DocumentRowMapper {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy    HH:mm");

	private DocumentRowMapper() {
	}

	public static DocumentRow toRow(DocumentEntry entry) {

		String status = statusLabel(entry.status());

		if (entry.invoice() == null) {
			return new DocumentRow("", "", "", "", entry.fileName(), status);
		}

		XmlDocument.Invoice invoice = entry.invoice();

		return new DocumentRow(
				String.valueOf(invoice.number()),
				String.valueOf(invoice.series()),
				NumberFormat.getCurrencyInstance(BRAZIL).format(invoice.totalValue()),
				DATE_FORMAT.format(invoice.issuedAt()),
				invoice.key().value(),
				status);
	}

	private static String statusLabel(DocumentStatus status) {
		return switch (status) {
			case AUTHORIZED -> "Autorizada";
			case CANCELLED -> "Cancelada";
			case VOIDED -> "Inutilizada";
			case ERROR -> "Erro na Leitura";
		};
	}
}