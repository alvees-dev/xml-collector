package com.alveesdev.xmlcollector.ui.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;

import javafx.application.Platform;
import javafx.concurrent.Task;

public class CollectionTask extends Task<CollectionResult> {

	private final CollectionService service;
	private final CollectionRequest request;
	private final Consumer<CollectionProgress> onProgress;

	// One entry per source, so a burst of file events reaches the screen as its latest state.
	private final Map<Integer, CollectionProgress> pending = new ConcurrentHashMap<>();
	private final AtomicBoolean deliveryScheduled = new AtomicBoolean();

	public CollectionTask(CollectionService service, CollectionRequest request, Consumer<CollectionProgress> onProgress) {
		this.service = service;
		this.request = request;
		this.onProgress = onProgress;
	}

	@Override
	protected CollectionResult call() {
		return service.collect(request, this::publishProgress, this::isCancelled);
	}

	private void publishProgress(CollectionProgress progress) {
		pending.put(progress.sourceIndex(), progress);

		if (deliveryScheduled.compareAndSet(false, true)) {
			Platform.runLater(this::deliverProgress);
		}
	}

	private void deliverProgress() {
		deliveryScheduled.set(false);

		for (Integer sourceIndex : List.copyOf(pending.keySet())) {
			CollectionProgress progress = pending.remove(sourceIndex);

			if (progress != null) {
				onProgress.accept(progress);
			}
		}
	}
}
