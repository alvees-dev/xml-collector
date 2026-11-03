package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.scene.Node;

final class CollectionRunner {

	private CollectionRunner() {
	}

	static void run(Node trigger, CollectionService service, CollectionRequest request, SourceMode sourceMode,
			Consumer<CollectionResult> onSuccess) {

		ProgressWindow progressWindow;

		try {
			progressWindow = new ProgressWindow(trigger.getScene().getWindow(), sourceLabels(request, sourceMode));
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
			Dialogs.error("Erro ao processar o diretório: " + task.getException().getMessage());
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
