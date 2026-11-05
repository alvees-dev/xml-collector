package com.alveesdev.xmlcollector.application.voiding;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

// Reads the database, the certificate and the PDV folders, so it must not run on the thread of the screen.
public final class VoidingPreparation {

	public record Plan(List<VoidingRequest> requests, Map<Integer, List<Path>> pdvFolders) {
	}

	private final VoidingPlanner planner;
	private final VoidingBatch batch;

	public VoidingPreparation(VoidingPlanner planner, VoidingBatch batch) {
		this.planner = planner;
		this.batch = batch;
	}

	public Plan prepare(MissingNumbersSession session, Map<Integer, List<NumberRange>> wanted, CertificateSettings certificate,
			List<String> pdvDirectories, String voidingFolder, Path archiveRoot) throws VoidingException {

		List<VoidingRequest> requests = planner.plan(wanted, session.missing(), session.ownersBySeries());
		batch.checkCertificate(certificate, requests);

		// The month of the collection, so collecting that period again reads the file back.
		Map<Integer, List<Path>> pdvFolders = VoidingPlanner.pdvFolders(requests, pdvDirectories, session.sourcesBySeries(), voidingFolder,
				YearMonth.from(session.lastCollectedDay()));
		batch.checkPdvFolders(pdvFolders);
		batch.checkArchiveFolder(archiveRoot, session.lastCollectedDay());

		return new Plan(requests, pdvFolders);
	}
}
