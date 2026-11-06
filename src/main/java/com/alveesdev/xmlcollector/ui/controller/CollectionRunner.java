package com.alveesdev.xmlcollector.ui.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.ui.model.CollectionActivity;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.scene.Node;

final class CollectionRunner {

	private static final double SOURCE_LABEL_WIDTH = 60;

	private CollectionRunner() {
	}

	static void run(Node trigger, String title, CollectionService service, CollectionRequest request, SourceMode sourceMode,
			Consumer<CollectionResult> onSuccess) {
		start(trigger, title, service, request, sourceMode, false, onSuccess, "Coleta cancelada. Os arquivos já copiados foram mantidos.");
	}

	static void read(Node trigger, String title, CollectionService service, CollectionRequest request, SourceMode sourceMode,
			Consumer<CollectionResult> onSuccess) {
		start(trigger, title, service, request, sourceMode, true, onSuccess, "Leitura cancelada.");
	}

	private static void start(Node trigger, String title, CollectionService service, CollectionRequest request, SourceMode sourceMode,
			boolean onlyReading, Consumer<CollectionResult> onSuccess, String cancelledMessage) {

		CollectionActivity activity = new CollectionActivity(request, sourceMode);
		activity.started(title);

		TaskRunner.withProgress(trigger, "xml-collection",
				() -> new ProgressWindow(trigger.getScene().getWindow(), title, sourceLabels(request, sourceMode), SOURCE_LABEL_WIDTH),
				window -> new CollectionTask(service, request, onlyReading, window::update, activity::record),
				new TaskRunner.Handlers<>(onSuccess,
						error -> Dialogs.error("Erro ao processar o diretório: " + FailureText.of(error)),
						() -> Dialogs.warning(cancelledMessage)));
	}

	private static Map<Integer, String> sourceLabels(CollectionRequest request, SourceMode sourceMode) {

		Map<Integer, String> labels = new LinkedHashMap<>();

		for (int index = 0; index < request.directories().size(); index++) {
			if (request.target().source().test(index)) {
				labels.put(index, SourceLabels.label(index, sourceMode));
			}
		}

		return labels;
	}
}
