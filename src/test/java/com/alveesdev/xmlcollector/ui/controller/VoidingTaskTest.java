package com.alveesdev.xmlcollector.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class VoidingTaskTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T12:00:00Z"), ZoneOffset.UTC);
	private static final CertificateSettings CERTIFICATE = new CertificateSettings("a.pfx", "x", FiscalEnvironment.HOMOLOGATION);
	private static final VoidingReceipt APPROVED = new VoidingReceipt("102", "Homologado", "135", "agora", "<xml/>");

	private final List<VoidingRequest> sent = new ArrayList<>();
	private VoidingTask task;

	@Test
	void withoutCancellingEveryRangeGoesOut() throws Exception {
		task = task(request -> {
			sent.add(request);
			return APPROVED;
		});

		assertEquals(List.of(Stage.APPROVED, Stage.APPROVED), stagesOf(task.call()));
	}

	// Cancelling must not interrupt a request on its way, or its answer would be lost.
	@Test
	void cancellingStopsTheNextRangesAndKeepsWhatWasDone() throws Exception {
		task = task(request -> {
			sent.add(request);
			task.cancel();
			return APPROVED;
		});

		assertEquals(List.of(Stage.APPROVED, Stage.NOT_SENT), stagesOf(task.call()));
		assertEquals(1, sent.size());
		assertFalse(task.isCancelled());
	}

	private VoidingTask task(com.alveesdev.xmlcollector.application.port.VoidingGateway gateway) {
		VoidingGatewayFactory gateways = settings -> new VoidingGatewayFactory.Connection(gateway, new VoidingGatewayFactory.CertificateInfo("12345678000190", CLOCK.instant()));
		DocumentArchive archive = (content, modified, directory, preferredName, canonicalName, identity) -> DocumentArchive.Outcome.COPIED;

		return new VoidingTask(new VoidingBatch(gateways, archive, CLOCK), List.of(request(1), request(2)), CERTIFICATE, Path.of("arquivo"),
				LocalDate.of(2026, 1, 31), Map.of());
	}

	private static VoidingRequest request(int number) {
		return new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, 1, new NumberRange(number, number), VoidingRequest.STANDARD_JUSTIFICATION);
	}

	private static List<Stage> stagesOf(List<VoidingOutcome> outcomes) {
		return outcomes.stream().map(VoidingOutcome::stage).toList();
	}
}
