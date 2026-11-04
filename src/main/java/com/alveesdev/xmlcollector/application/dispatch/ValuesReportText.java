package com.alveesdev.xmlcollector.application.dispatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

public final class ValuesReportText {

	public static final String FILE_NAME = "Relatório de valores.txt";

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final String LINE_BREAK = "\r\n";
	private static final String BYTE_ORDER_MARK = "﻿";
	private static final String NONE = "Nenhuma";
	private static final String NO_NUMBER = "-";

	private ValuesReportText() {
	}

	// The mark makes every Windows program open the accents correctly.
	public static byte[] bytesOf(String monthLabel, ValuesReport report) {
		return (BYTE_ORDER_MARK + textOf(monthLabel, report)).getBytes(StandardCharsets.UTF_8);
	}

	static String textOf(String monthLabel, ValuesReport report) {

		List<String> lines = new ArrayList<>();
		lines.add("RELATÓRIO DE VALORES - " + monthLabel);

		if (report.unreadableFiles() > 0) {
			lines.add("");
			lines.add("ATENÇÃO: " + report.unreadableFiles() + " arquivo(s) não puderam ser lidos e não entram nestes números.");
		}

		if (report.sections().isEmpty()) {
			lines.add("");
			lines.add("Nenhuma nota encontrada.");
		}

		for (ValuesReport.Section section : report.sections()) {
			lines.add("");
			lines.add("CNPJ " + section.cnpj() + " - " + modelName(section.modelCode()) + " - Série " + section.series());
			lines.add("Primeira nota emitida: " + numberOf(section.firstNumber()));
			lines.add("Última nota emitida: " + numberOf(section.lastNumber()));
			lines.add("Notas canceladas: " + cancelledOf(section.cancelledNumbers()));
			lines.add("Notas inutilizadas: " + voidedOf(section.voidedRanges()));
			lines.add("Total de vendas: " + salesOf(section.sales()));
			lines.add("Valor total das vendas: " + currency(section.salesValue()));
		}

		lines.add("");
		lines.add("TOTAL DO MÊS");
		lines.add("Total de vendas: " + salesOf(report.totalSales()));
		lines.add("Valor total das vendas: " + currency(report.totalValue()));

		return String.join(LINE_BREAK, lines) + LINE_BREAK;
	}

	private static String modelName(String modelCode) {
		return DocumentModel.fromCode(modelCode)
				.map(model -> model == DocumentModel.NFCE ? "NFC-e" : "NF-e")
				.orElse("Modelo " + modelCode);
	}

	private static String numberOf(Integer number) {
		return number == null ? NO_NUMBER : number.toString();
	}

	private static String cancelledOf(List<Integer> numbers) {
		if (numbers.isEmpty()) {
			return NONE;
		}

		return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}

	private static String voidedOf(List<NumberRange> ranges) {
		if (ranges.isEmpty()) {
			return NONE;
		}

		return ranges.stream()
				.map(range -> range.size() == 1 ? String.valueOf(range.start()) : range.start() + " a " + range.end())
				.collect(Collectors.joining(", "));
	}

	private static String salesOf(int sales) {
		return sales + (sales == 1 ? " nota" : " notas");
	}

	private static String currency(BigDecimal value) {
		return NumberFormat.getCurrencyInstance(BRAZIL).format(value).replace(' ', ' ');
	}
}
