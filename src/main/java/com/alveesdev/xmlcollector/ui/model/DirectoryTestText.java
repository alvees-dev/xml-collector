package com.alveesdev.xmlcollector.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult;

public final class DirectoryTestText {

	public static final String RUNNING = "Testando...";

	private DirectoryTestText() {
	}

	public static String of(DirectoryTestResult result) {
		return switch (result.outcome()) {
			case NOT_INFORMED -> "Diretório não informado";
			case NO_CONNECTION -> "Sem conexão ou diretório não encontrado";
			case NO_PERMISSION -> "Sem permissão de leitura";
			case SEARCH_TOO_SLOW -> "Conectou, mas a busca das pastas demorou";
			case CONNECTED -> connected(result);
		};
	}

	// One line per directory, in the order of the settings, as in "PDV 01: OK".
	public static String summary(Map<String, DirectoryTestResult> results) {
		return results.entrySet().stream()
				.map(entry -> entry.getKey() + ": " + of(entry.getValue()).replace("\n", "; "))
				.collect(Collectors.joining("\n"));
	}

	private static String connected(DirectoryTestResult result) {

		List<String> lines = new ArrayList<>();

		// The missing folders already say it when the model has required ones.
		if (!result.anyModelFolder() && result.missingFolders().isEmpty()) {
			lines.add("Conectou, mas nenhuma pasta do modelo foi encontrada");
		}

		for (String folder : result.missingFolders()) {
			lines.add("Conectou, mas falta a pasta " + folder);
		}

		if (!result.writable()) {
			lines.add("Conectou, mas sem permissão de gravação");
		}

		return lines.isEmpty() ? "OK" : String.join("\n", lines);
	}
}
