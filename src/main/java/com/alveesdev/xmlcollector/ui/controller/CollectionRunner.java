package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.scene.Node;

final class CollectionRunner {

	private static final Logger LOG = Logger.getLogger(CollectionRunner.class.getName());
	private static final double SOURCE_LABEL_WIDTH = 60;

	private CollectionRunner() {
	}

	static void run(Node trigger, String title, CollectionService service, CollectionRequest request, SourceMode sourceMode,
			Consumer<CollectionResult> onSuccess) {

		ProgressWindow progressWindow;

		try {
			progressWindow = new ProgressWindow(trigger.getScene().getWindow(), title, sourceLabels(request, sourceMode), SOURCE_LABEL_WIDTH);
		} catch (IOException e) {
			Dialogs.error("Erro ao abrir a tela de progresso: " + e.getMessage());
			return;
		}

		CollectionTask task = new CollectionTask(service, request, progressWindow::update);
		progressWindow.setCancelAction(task::cancel);

		trigger.setDisable(true);

		task.setOnSucceeded(event -> {
			progressWindow.close();
			trigger.setDisable(false);
			onSuccess.accept(task.getValue());
		});

		task.setOnFailed(event -> {
			progressWindow.close();
			trigger.setDisable(false);
			LOG.log(Level.SEVERE, "The collection failed", task.getException());
			Dialogs.error("Erro ao processar o diretório: " + FailureText.of(task.getException()));
		});

		task.setOnCancelled(event -> {
			progressWindow.close();
			trigger.setDisable(false);
			Dialogs.warning("Coleta cancelada. Os arquivos já copiados foram mantidos.");
		});

		progressWindow.show();

		Thread collectThread = new Thread(task);
		collectThread.setDaemon(true);
		collectThread.start();
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
