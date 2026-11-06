package com.alveesdev.xmlcollector.application.dispatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.ReportPdfWriter;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

public final class ValuesReportText {

	private static final String FILE_EXTENSION = ".txt";

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final String LINE_BREAK = "\r\n";
	private static final String BYTE_ORDER_MARK = "\uFEFF";
	private static final String NONE = "Nenhuma";
	private static final String NO_NUMBER = "-";

	private ValuesReportText() {
	}

	// One file for the NFCe and another for the NFe, each only when one of its kinds is checked.
	public static List<DirectoryCompressor.ExtraFile> filesOf(DispatchMonth month, ValuesReport company, ValuesReport purchases, ReportOptions options) {

		List<DirectoryCompressor.ExtraFile> files = new ArrayList<>();

		if (options.nfce()) {
			files.add(new DirectoryCompressor.ExtraFile(nfceFileName(month), bytesOf(nfceTextOf(month.label(), company, purchases))));
		}

		if (options.nfeSales() || options.nfePurchases()) {
			files.add(new DirectoryCompressor.ExtraFile(nfeFileName(month), bytesOf(nfeTextOf(month.label(), company, purchases, options))));
		}

		return files;
	}

	public static String nfceFileName(DispatchMonth month) {
		return "Relatório NFCe " + month.monthAndYear() + FILE_EXTENSION;
	}

	public static String nfeFileName(DispatchMonth month) {
		return "Relatório NFe " + month.monthAndYear() + FILE_EXTENSION;
	}

	public static String pdfFileName(DispatchMonth month) {
		return "Relatório " + month.monthAndYear() + ".pdf";
	}

	// The same text as the files of the zip, the parts that are checked one after the other in a single document.
	public static List<ReportPdfWriter.Line> pdfLinesOf(DispatchMonth month, ValuesReport company, ValuesReport purchases, ReportOptions options) {

		List<String> texts = new ArrayList<>();

		if (options.nfce()) {
			texts.add(nfceTextOf(month.label(), company, purchases));
		}

		if (options.nfeSales() || options.nfePurchases()) {
			texts.add(nfeTextOf(month.label(), company, purchases, options));
		}

		List<ReportPdfWriter.Line> lines = new ArrayList<>();

		for (String text : texts) {
			if (!lines.isEmpty()) {
				lines.add(new ReportPdfWriter.Line("", false));
			}

			for (String line : text.split(LINE_BREAK)) {
				lines.add(new ReportPdfWriter.Line(line, isHeading(line)));
			}
		}

		return lines;
	}

	private static boolean isHeading(String line) {
		return line.startsWith("RELATÓRIO ") || line.startsWith("NF-e - ") || line.startsWith("TOTAL DO MÊS")
				|| line.startsWith("CNPJ ") || line.startsWith("ATENÇÃO:");
	}

	// The mark makes every Windows program open the accents correctly.
	static byte[] bytesOf(String text) {
		return (BYTE_ORDER_MARK + text).getBytes(StandardCharsets.UTF_8);
	}

	static String nfceTextOf(String monthLabel, ValuesReport company, ValuesReport purchases) {

		List<ValuesReport.Section> detailed = sectionsOtherThanNfe(company);

		List<String> lines = new ArrayList<>();
		lines.add("RELATÓRIO NFCe - " + monthLabel);
		addUnreadable(lines, company, purchases);

		if (detailed.isEmpty()) {
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

		if (!detailed.isEmpty()) {
			lines.add("");
			lines.add("TOTAL DO MÊS");
			lines.add("Total de vendas: " + salesOf(detailed.stream().mapToInt(ValuesReport.Section::sales).sum()));
			lines.add("Valor total das vendas: " + currency(valueOf(detailed)));
		}

		return String.join(LINE_BREAK, lines) + LINE_BREAK;
	}

	// A purchase is money going out, so it stays apart from the sales.
	static String nfeTextOf(String monthLabel, ValuesReport company, ValuesReport purchases, ReportOptions options) {

		List<String> lines = new ArrayList<>();
		lines.add("RELATÓRIO NFe - " + monthLabel);
		addUnreadable(lines, company, purchases);

		if (options.nfeSales()) {
			List<ValuesReport.Section> sales = nfeSectionsOf(company);

			lines.add("");
			lines.add("NF-e - VENDAS");
			lines.add("Total de vendas: " + salesOf(sales.stream().mapToInt(ValuesReport.Section::sales).sum()));
			lines.add("Valor total das vendas: " + currency(valueOf(sales)));
		}

		if (options.nfePurchases()) {
			lines.add("");
			lines.add("NF-e - COMPRAS");
			lines.add("Valor total das compras: " + currency(purchases.totalValue()));
		}

		return String.join(LINE_BREAK, lines) + LINE_BREAK;
	}

	private static void addUnreadable(List<String> lines, ValuesReport company, ValuesReport purchases) {

		int unreadable = company.unreadableFiles() + purchases.unreadableFiles();

		if (unreadable > 0) {
			lines.add("");
			lines.add("ATENÇÃO: " + unreadable + " arquivo(s) não puderam ser lidos e não entram nestes números.");
		}
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
