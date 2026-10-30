package com.alveesdev.xmlcollector.ui.model;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.alveesdev.xmlcollector.domain.model.NfceEntry;
import com.alveesdev.xmlcollector.domain.model.NfceStatus;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class NfceRowMapper {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy    HH:mm");

	private NfceRowMapper() {
	}

	public static NfceRow toRow(NfceEntry entry) {

		String status = statusLabel(entry.status());

		if (entry.nfce() == null) {
			return new NfceRow("", "", "", "", entry.fileName(), status);
		}

		XmlDocument.Nfce nfce = entry.nfce();

		return new NfceRow(
				String.valueOf(nfce.number()),
				String.valueOf(nfce.series()),
				NumberFormat.getCurrencyInstance(BRAZIL).format(nfce.totalValue()),
				DATE_FORMAT.format(nfce.issuedAt()),
				nfce.key().value(),
				status);
	}

	private static String statusLabel(NfceStatus status) {
		return switch (status) {
			case AUTHORIZED -> "Autorizada";
			case CANCELLED -> "Cancelada";
			case VOIDED -> "Inutilizada";
			case ERROR -> "Erro na Leitura";
		};
	}
}