package com.alveesdev.xmlcollector.ui.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

import javafx.application.Platform;
import javafx.concurrent.Task;

public class VoidingTask extends Task<List<VoidingOutcome>> {

	private final VoidingBatch batch;
	private final List<VoidingRequest> requests;
	private final CertificateSettings certificate;
	private final OrganizeMode organizeMode;
	private final Path archiveRoot;
	private final Consumer<VoidingProgress> onProgress;

	private final AtomicBoolean stopRequested = new AtomicBoolean();

	public VoidingTask(VoidingBatch batch, List<VoidingRequest> requests, CertificateSettings certificate,
			OrganizeMode organizeMode, Path archiveRoot, Consumer<VoidingProgress> onProgress) {
		this.batch = batch;
		this.requests = requests;
		this.certificate = certificate;
		this.organizeMode = organizeMode;
		this.archiveRoot = archiveRoot;
		this.onProgress = onProgress;
	}

	// A request on its way cannot be called back, so stopping only prevents the next ones and its answer is still shown.
	public void requestStop() {
		stopRequested.set(true);
	}

	@Override
	protected List<VoidingOutcome> call() throws Exception {
		return batch.run(requests, certificate, organizeMode, archiveRoot,
				progress -> Platform.runLater(() -> onProgress.accept(progress)), stopRequested::get);
	}
}
