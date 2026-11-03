package com.alveesdev.xmlcollector.application.voiding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGateway;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class VoidingBatchTest {

	private static final Path ROOT = Path.of("archive");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T12:00:00Z"), ZoneOffset.UTC);
	private static final CertificateSettings SETTINGS = new CertificateSettings("a.pfx", "x", FiscalEnvironment.PRODUCTION);
	private static final VoidingReceipt APPROVED = new VoidingReceipt("102", "Homologado", "135", "agora", "<xml/>");
	private static final VoidingReceipt REJECTED = new VoidingReceipt("241", "Numero ja utilizado", null, "agora", "<xml/>");

	private static final VoidingRequest FIRST = request(1);
	private static final VoidingRequest SECOND = request(2);
	private static final VoidingRequest THIRD = request(3);

	private final List<VoidingRequest> sent = new ArrayList<>();
	private final List<VoidingProgress> events = new ArrayList<>();
	private final List<String> stored = new ArrayList<>();

	private final DocumentArchive archive = new DocumentArchive() {

		@Override
		public Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
				String canonicalName, String identity) throws IOException {
			stored.add(preferredName);
			return Outcome.COPIED;
		}
	};

	@Test
	void sendsEveryRangeInOrderAndReportsEach() throws Exception {
		List<VoidingOutcome> outcomes = batch("12345678000190", request -> APPROVED).run(List.of(FIRST, SECOND), SETTINGS,
				OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> false);

		assertEquals(List.of(FIRST, SECOND), sent);
		assertEquals(List.of(Stage.APPROVED, Stage.APPROVED), outcomes.stream().map(VoidingOutcome::stage).toList());
		assertEquals(List.of(
				new VoidingProgress(0, Stage.SENDING), new VoidingProgress(0, Stage.APPROVED),
				new VoidingProgress(1, Stage.SENDING), new VoidingProgress(1, Stage.APPROVED)), events);
		assertEquals(2, stored.size());
	}

	@Test
	void aRejectionDoesNotStopTheNextRangeAndIsNotStored() throws Exception {
		List<VoidingOutcome> outcomes = batch("12345678000190", request -> request == FIRST ? REJECTED : APPROVED)
				.run(List.of(FIRST, SECOND), SETTINGS, OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> false);

		assertEquals(List.of(Stage.REJECTED, Stage.APPROVED), outcomes.stream().map(VoidingOutcome::stage).toList());
		assertTrue(outcomes.get(0).detail().contains("241"));
		assertEquals(1, stored.size());
	}

	@Test
	void anUnknownAnswerStopsEverythingElse() throws Exception {
		List<VoidingOutcome> outcomes = batch("12345678000190", request -> {
			if (request == SECOND) {
				throw new VoidingException("sem resposta");
			}
			return APPROVED;
		}).run(List.of(FIRST, SECOND, THIRD), SETTINGS, OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> false);

		assertEquals(List.of(FIRST, SECOND), sent);
		assertEquals(List.of(Stage.APPROVED, Stage.FAILED, Stage.NOT_SENT), outcomes.stream().map(VoidingOutcome::stage).toList());
	}

	@Test
	void stoppingKeepsWhatWasDoneAndSendsNothingElse() throws Exception {
		List<VoidingOutcome> outcomes = batch("12345678000190", request -> APPROVED).run(List.of(FIRST, SECOND), SETTINGS,
				OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> !sent.isEmpty());

		assertEquals(List.of(FIRST), sent);
		assertEquals(List.of(Stage.APPROVED, Stage.NOT_SENT), outcomes.stream().map(VoidingOutcome::stage).toList());
	}

	@Test
	void refusesACertificateOfAnotherCompanyBeforeSendingAnything() {
		VoidingException error = assertThrows(VoidingException.class, () -> batch("98765432000110", request -> APPROVED)
				.run(List.of(FIRST), SETTINGS, OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> false));

		assertTrue(error.getMessage().contains("98765432000110"));
		assertEquals(List.of(), sent);
	}

	@Test
	void acceptsTheCertificateOfAnotherBranchOfTheSameCompany() throws Exception {
		batch("12345678000271", request -> APPROVED).run(List.of(FIRST), SETTINGS, OrganizeMode.SINGLE_FOLDER, ROOT, events::add, () -> false);

		assertEquals(List.of(FIRST), sent);
	}

	private VoidingBatch batch(String certificateCnpj, VoidingGateway gateway) {
		VoidingGateway recording = request -> {
			sent.add(request);
			return gateway.voidRange(request);
		};

		return new VoidingBatch(
				settings -> new VoidingGatewayFactory.Connection(recording, new VoidingGatewayFactory.CertificateInfo(certificateCnpj, Instant.MAX)),
				archive, CLOCK);
	}

	private static VoidingRequest request(int number) {
		return new VoidingRequest("35", 2026, "12345678000190", DocumentModel.NFCE, 1, new NumberRange(number, number),
				VoidingRequest.STANDARD_JUSTIFICATION);
	}
}
