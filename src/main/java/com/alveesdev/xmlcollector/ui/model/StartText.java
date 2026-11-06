package com.alveesdev.xmlcollector.ui.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// What the person is about to start, so a click by mistake can still be stopped.
public final class StartText {

	public static final String COLLECTION_HEADER = "Iniciar a coleta?";
	public static final String READING_HEADER = "Iniciar a leitura?";

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private StartText() {
	}

	// The source only matters for the NFCe, because the NFe always come from their own directories.
	public static String collection(List<String> kinds, SourceMode nfceSource, LocalDate from, LocalDate to) {

		StringBuilder text = new StringBuilder();
		text.append("Coletar: ").append(String.join(", ", kinds));

		if (nfceSource != null) {
			text.append("\nOrigem da NFCe: ").append(SourceLabels.name(nfceSource));
		}

		text.append("\nPeríodo: ").append(periodOf(from, to));

		return text.toString();
	}

	// The directory is told only when the source does not already name it, as the folder of the program for an empty Diretório Único.
	public static String reading(SourceMode source, String directory, LocalDate from, LocalDate to) {
		return "Ler: NFCe"
				+ "\nOrigem: " + SourceLabels.name(source)
				+ (directory == null ? "" : "\nDiretório: " + directory)
				+ "\nPeríodo: " + periodOf(from, to)
				+ "\n\nNenhum arquivo será copiado.";
	}

	private static String periodOf(LocalDate from, LocalDate to) {
		return DATE_FORMAT.format(from) + " a " + DATE_FORMAT.format(to);
	}
}
