package com.alveesdev.xmlcollector.ui.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public final class CollectionSummaryText {

	private static final int MAX_LISTED = 15;

	private CollectionSummaryText() {
	}

	public static String collection(CollectionResult result, Path archiveRoot, SourceMode sourceMode) {

		CollectionSummary summary = result.summary();

		StringBuilder message = new StringBuilder();
		message.append("Copiados: ").append(summary.copied());

		if (summary.renamed() > 0) {
			message.append("\nRenomeados pela chave (nome repetido): ").append(summary.renamed());
		}

		if (summary.replaced() > 0) {
			message.append("\nSubstituídos (arquivo danificado no destino): ").append(summary.replaced());
		}

		message.append("\nJá existiam no destino: ").append(summary.alreadyExisting());
		message.append("\nFora do período: ").append(summary.outOfPeriod());
		message.append("\nCom erro de leitura: ").append(summary.errors());
		message.append("\n\nArquivos salvos em: ").append(archiveRoot);

		appendFailures(message, failures(result, sourceMode));

		return message.toString();
	}

	public static String reading(CollectionResult result, SourceMode sourceMode) {

		long notes = result.entries().stream().filter(entry -> entry.invoice() != null).count();

		StringBuilder message = new StringBuilder();
		message.append("Notas lidas: ").append(notes);
		message.append("\nFora do período: ").append(result.summary().outOfPeriod());
		message.append("\nCom erro de leitura: ").append(result.summary().errors());
		message.append("\n\nNenhum arquivo foi copiado.");

		appendFailures(message, failures(result, sourceMode));

		return message.toString();
	}

	public static boolean collectionHasProblems(CollectionResult result, SourceMode sourceMode) {
		return !failures(result, sourceMode).isEmpty() || result.summary().errors() > 0;
	}

	public static String missingSearch(CollectionResult result, List<XmlDocument.Invoice> found, Path archiveRoot) {

		StringBuilder message = new StringBuilder();
		message.append("Encontradas: ").append(found.size());

		found.stream()
				.sorted(Comparator.comparingInt(XmlDocument.Invoice::series).thenComparingInt(XmlDocument.Invoice::number))
				.limit(MAX_LISTED)
				.forEach(invoice -> message.append("\nSérie ").append(invoice.series()).append(": ").append(invoice.number()));

		if (found.size() > MAX_LISTED) {
			message.append("\n... e mais ").append(found.size() - MAX_LISTED);
		}

		message.append("\n\nCom erro de leitura: ").append(result.summary().errors());
		message.append("\nDestino: ").append(archiveRoot);

		appendFailures(message, failures(result, SourceMode.PDV_DIRECTORIES));

		return message.toString();
	}

	public static boolean missingSearchHasProblems(CollectionResult result) {
		return !failures(result, SourceMode.PDV_DIRECTORIES).isEmpty();
	}

	private static void appendFailures(StringBuilder message, List<String> failures) {
		if (!failures.isEmpty()) {
			message.append("\n\nFalhas:\n").append(String.join("\n", failures));
		}
	}

	private static List<String> failures(CollectionResult result, SourceMode sourceMode) {

		List<String> lines = new ArrayList<>();

		for (SourceFailure failure : result.failures()) {
			String label = SourceLabels.label(failure.sourceIndex(), sourceMode);

			lines.add(switch (failure.reason()) {
				case NOT_INFORMED -> label + ": diretório não informado";
				case INACCESSIBLE -> label + ": diretório inacessível (" + failure.path() + ")";
				case NO_MODEL_FOLDER -> label + ": nenhuma pasta do modelo de busca encontrada (" + failure.path() + ")";
				case FOLDER_NOT_FOUND -> label + ": pasta não encontrada (" + failure.path() + ")";
				case SUBFOLDER_INACCESSIBLE -> label + ": subpasta inacessível (" + failure.path() + ")";
			});
		}

		if (result.historyFailure() != null) {
			lines.add(result.historyFailure());
		}

		for (CollectionSummary.CopyFailure failure : result.summary().copyFailures()) {
			lines.add("Falha ao copiar " + failure.fileName() + ": " + failure.reason());
		}

		if (lines.size() > MAX_LISTED) {
			int hidden = lines.size() - MAX_LISTED;
			List<String> limited = new ArrayList<>(lines.subList(0, MAX_LISTED));
			limited.add("... e mais " + hidden + " falha(s)");
			return limited;
		}

		return lines;
	}
}
