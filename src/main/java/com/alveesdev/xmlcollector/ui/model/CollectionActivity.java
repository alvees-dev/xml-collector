package com.alveesdev.xmlcollector.ui.model;

import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;

public final class CollectionActivity {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CollectionRequest request;
	private final SourceMode sourceMode;

	public CollectionActivity(CollectionRequest request, SourceMode sourceMode) {
		this.request = request;
		this.sourceMode = sourceMode;
	}

	public void started(String title) {
		ActivityLog.record(title + " por " + sourceName() + ", de " + DATE_FORMAT.format(request.period().from())
				+ " a " + DATE_FORMAT.format(request.period().to()));
	}

	public void record(CollectionProgress progress) {

		String source = SourceLabels.label(progress.sourceIndex(), sourceMode);

		switch (progress.stage()) {
			case COUNTING -> ActivityLog.record(source + ": procurando arquivos em " + request.directories().get(progress.sourceIndex()));
			case READING -> {
				// Only the first event of each source, because the others are one per file.
				if (progress.processed() == 0) {
					ActivityLog.record(source + ": " + progress.total() + " arquivo(s) encontrado(s), coletando");
				}
			}
			case DONE -> ActivityLog.record(source + ": arquivos coletados");
			case DONE_WITH_WARNING -> ActivityLog.record(Level.WARNING, source + ": arquivos coletados, mas uma parte das pastas não foi lida");
			case FAILED -> ActivityLog.record(Level.WARNING, source + ": não foi possível coletar");
		}
	}

	private String sourceName() {
		return switch (sourceMode) {
			case PDV_DIRECTORIES -> "Diretório PDV";
			case NFE_DIRECTORIES -> "Diretório NFe";
			case SINGLE_DIRECTORY -> "Diretório Único";
		};
	}
}
