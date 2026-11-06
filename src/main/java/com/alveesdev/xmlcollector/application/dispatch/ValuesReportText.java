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
	private static final String BYTE_ORDER_MARK = "\uFEFF";
	private static final String NONE = "Nenhuma";
	private static final String NO_NUMBER = "-";

	private ValuesReportText() {
	}

	// The mark makes every Windows program open the accents correctly.
	public static byte[] bytesOf(String monthLabel, ValuesReport company, ValuesReport purchases, ReportOptions options) {
		return (BYTE_ORDER_MARK + textOf(monthLabel, company, purchases, options)).getBytes(StandardCharsets.UTF_8);
	}

	static String textOf(String monthLabel, ValuesReport company, ValuesReport purchases, ReportOptions options) {

		List<ValuesReport.Section> detailed = options.nfce() ? sectionsOtherThanNfe(company) : List.of();
		List<ValuesReport.Section> nfeSales = options.nfeSales() ? nfeSectionsOf(company) : List.of();

		List<String> lines = new ArrayList<>();
		lines.add("RELATÓRIO DE VALORES - " + monthLabel);

		int unreadable = company.unreadableFiles() + purchases.unreadableFiles();

		if (unreadable > 0) {
			lines.add("");
			lines.add("ATENÇÃO: " + unreadable + " arquivo(s) não puderam ser lidos e não entram nestes números.");
		}

		if (detailed.isEmpty() && !options.nfeSales() && !options.nfePurchases()) {
			lines.add("");
			lines.add("Nenhuma nota encontrada.");
		}

		for (ValuesReport.Section section : detailed) {
			lines.add("");
			lines.add("CNPJ " + section.cnpj() + " - " + modelName(section.modelCode()) + " - Série " + section.series());
			lines.add("Primeira nota emitida: " + numberOf(section.firstNumber()));
			lines.add("Última nota emitida: " + numberOf(section.lastNumber()));
			lines.add("Notas canceladas: " + cancelledOf(section.cancelledNumbers()));
			lines.add("Notas inutilizadas: " + voidedOf(section.voidedRanges()));
			lines.add("Total de vendas: " + salesOf(section.sales()));
			lines.add("Valor total das vendas: " + currency(section.salesValue()));
		}

		if (options.nfeSales()) {
			lines.add("");
			lines.add("NF-e - VENDAS");
			lines.add("Valor total das vendas: " + currency(valueOf(nfeSales)));
		}

		if (options.nfePurchases()) {
			lines.add("");
			lines.add("NF-e - COMPRAS");
			lines.add("Valor total das compras: " + currency(purchases.totalValue()));
		}

		// A purchase is money going out, so it never joins the total of the sales.
		if (options.anySales()) {
			List<ValuesReport.Section> sales = new ArrayList<>(detailed);
			sales.addAll(nfeSales);

			lines.add("");
			lines.add("TOTAL DO MÊS");
			lines.add("Total de vendas: " + salesOf(sales.stream().mapToInt(ValuesReport.Section::sales).sum()));
			lines.add("Valor total das vendas: " + currency(valueOf(sales)));
		}

		return String.join(LINE_BREAK, lines) + LINE_BREAK;
	}

	private static List<ValuesReport.Section> sectionsOtherThanNfe(ValuesReport report) {
		return report.sections().stream().filter(section -> !DocumentModel.NFE.code().equals(section.modelCode())).toList();
	}

	private static List<ValuesReport.Section> nfeSectionsOf(ValuesReport report) {
		return report.sections().stream().filter(section -> DocumentModel.NFE.code().equals(section.modelCode())).toList();
	}

	private static BigDecimal valueOf(List<ValuesReport.Section> sections) {
		return sections.stream().map(ValuesReport.Section::salesValue).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static String modelName(String modelCode) {
		return DocumentModel.fromCode(modelCode)
				.map(model -> model == DocumentModel.NFCE ? "NFC-e" : "NF-e")
				.orElse("Modelo " + modelCode);
	}

	private static String numberOf(Integer number) {
		return number == null ? NO_NUMBER : number.toString();
	}

	// Only how many, because a list of numbers of five digits reads like a quantity.
	private static String cancelledOf(List<Integer> numbers) {
		return countOf(numbers.size());
	}

	private static String voidedOf(List<NumberRange> ranges) {
		return countOf(ranges.stream().mapToInt(NumberRange::size).sum());
	}

	private static String countOf(int notes) {
		return notes == 0 ? NONE : salesOf(notes);
	}

	private static String salesOf(int sales) {
		return sales + (sales == 1 ? " nota" : " notas");
	}

	private static String currency(BigDecimal value) {
		return NumberFormat.getCurrencyInstance(BRAZIL).format(value).replace('\u00A0', ' ');
	}
}
