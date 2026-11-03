package com.alveesdev.xmlcollector.ui.controller;

import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress;
import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;

import javafx.application.Platform;
import javafx.concurrent.Task;

public class DispatchTask extends Task<DispatchResult> {

	private final DispatchService service;
	private final DispatchRequest request;
	private final Consumer<DispatchProgress> onProgress;

	public DispatchTask(DispatchService service, DispatchRequest request, Consumer<DispatchProgress> onProgress) {
		this.service = service;
		this.request = request;
		this.onProgress = onProgress;
	}

	@Override
	protected DispatchResult call() throws Exception {
		return service.dispatch(request, progress -> Platform.runLater(() -> onProgress.accept(progress)), this::isCancelled);
	}
}
