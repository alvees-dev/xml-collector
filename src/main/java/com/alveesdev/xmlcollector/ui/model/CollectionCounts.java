package com.alveesdev.xmlcollector.ui.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// What the footer of the main screen tells about the notes on the screen.
public record CollectionCounts(String action, long xml, long cancelled, int voided, long errors, BigDecimal total, LocalDate from, LocalDate to) {

	public static final String NOTHING_YET = "Nenhuma coleta feita ainda";

	private static final NumberFormat THOUSANDS = NumberFormat.getIntegerInstance(Locale.of("pt", "BR"));
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public String actionText() {
		return action + ":";
	}

	public String xmlText() {
		return THOUSANDS.format(xml) + " XML";
	}

	public String cancelledText() {
		return "Canceladas: " + THOUSANDS.format(cancelled);
	}

	public String voidedText() {
		return "Inutilizadas: " + THOUSANDS.format(voided);
	}

	public String errorsText() {
		return "Com erro: " + THOUSANDS.format(errors);
	}

	// What was sold: the authorized NFCe and NFe Vendas, without the purchases.
	public String totalText() {
		return "Total: " + DocumentRowMapper.currency(total);
	}

	public String periodText() {
		return DATE.format(from) + " a " + DATE.format(to);
	}
}
