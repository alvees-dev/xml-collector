package com.alveesdev.xmlcollector.ui.model;

import java.util.Locale;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;

public final class SourceProgressText {

	public static final String WAITING = "Aguardando";

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

	private SourceProgressText() {
	}

	public static String of(CollectionProgress progress) {
		return switch (progress.stage()) {
			case COUNTING -> "Contando arquivos...";
			case READING -> String.format(BRAZIL, "%,d de %,d", progress.processed(), progress.total());
			case DONE -> String.format(BRAZIL, "OK - %,d %s", progress.total(), progress.total() == 1 ? "arquivo" : "arquivos");
			case FAILED -> "Falhou";
		};
	}
}
