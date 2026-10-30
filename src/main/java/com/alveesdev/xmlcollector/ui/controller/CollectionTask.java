package com.alveesdev.xmlcollector.ui.controller;

import java.util.Locale;

import com.alveesdev.xmlcollector.application.CollectionProgress;
import com.alveesdev.xmlcollector.application.CollectionRequest;
import com.alveesdev.xmlcollector.application.CollectionResult;
import com.alveesdev.xmlcollector.application.CollectionService;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;

import javafx.concurrent.Task;

public class CollectionTask extends Task<CollectionResult> {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

	private final CollectionService service;
	private final CollectionRequest request;
	private final boolean pdvMode;

	public CollectionTask(CollectionService service, CollectionRequest request, boolean pdvMode) {
		this.service = service;
		this.request = request;
		this.pdvMode = pdvMode;
	}

	@Override
	protected CollectionResult call() {
		return service.collect(request, this::publishProgress, this::isCancelled);
	}

	private void publishProgress(CollectionProgress progress) {
		updateTitle(SourceLabels.label(progress.sourceIndex(), pdvMode));

		if (progress.isCounting()) {
			updateProgress(-1, -1);
			updateMessage("Contando arquivos...");
			return;
		}

		updateProgress(progress.processed(), Math.max(progress.total(), 1));
		updateMessage(String.format(BRAZIL, "%,d de %,d arquivos", progress.processed(), progress.total()));
	}
}