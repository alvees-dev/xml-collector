package com.alveesdev.xmlcollector.ui.controller;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

import javafx.concurrent.Task;

public class VoidingTask extends Task<List<VoidingOutcome>> {

	private final VoidingBatch batch;
	private final List<VoidingRequest> requests;
	private final CertificateSettings certificate;
	private final Path archiveRoot;
	private final LocalDate archiveDate;
	private final Map<Integer, List<Path>> pdvFolders;

	private final AtomicBoolean stopRequested = new AtomicBoolean();

	public VoidingTask(VoidingBatch batch, List<VoidingRequest> requests, CertificateSettings certificate,
			Path archiveRoot, LocalDate archiveDate, Map<Integer, List<Path>> pdvFolders) {
		this.batch = batch;
		this.requests = requests;
		this.certificate = certificate;
		this.archiveRoot = archiveRoot;
		this.archiveDate = archiveDate;
		this.pdvFolders = pdvFolders;
	}

	// A request on its way cannot be called back, so cancelling only prevents the next ones and the answers are still shown.
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		stopRequested.set(true);
		return false;
	}

	@Override
	protected List<VoidingOutcome> call() throws Exception {
		return batch.run(requests, certificate, archiveRoot, archiveDate, pdvFolders, progress -> {
		}, stopRequested::get);
	}
}
