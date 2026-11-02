package com.alveesdev.xmlcollector.ui.controller;

import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;

import javafx.concurrent.Task;

public class DispatchTask extends Task<DispatchResult> {

	private final DispatchService service;
	private final DispatchRequest request;

	public DispatchTask(DispatchService service, DispatchRequest request) {
		this.service = service;
		this.request = request;
	}

	@Override
	protected DispatchResult call() throws Exception {
		return service.dispatch(request);
	}
}