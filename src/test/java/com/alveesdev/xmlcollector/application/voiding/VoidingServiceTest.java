package com.alveesdev.xmlcollector.application.voiding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.StubDocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class VoidingServiceTest {

	private static final Path ROOT = Path.of("archive");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T12:00:00Z"), ZoneOffset.UTC);
	private static final LocalDate COLLECTED = LocalDate.of(2026, 1, 31);
	private static final VoidingRequest REQUEST = new VoidingRequest(
			"35", 2026, "12345678000190", DocumentModel.NFCE, 1, new NumberRange(2, 3), "Numeracao nao utilizada");
	private static final VoidingReceipt APPROVED = new VoidingReceipt("102", "Inutilizacao homologada", "135", "agora", "<procInutNFe/>");
	private static final VoidingReceipt REJECTED = new VoidingReceipt("241", "Numero ja utilizado", null, "agora", "<procInutNFe/>");

	private final FakeArchive archive = new FakeArchive();

	@Test
	void storesTheProcessedXmlWhenApproved() throws Exception {
		VoidingResult result = service(APPROVED).voidRange(REQUEST, ROOT, COLLECTED, List.of());

		assertEquals(APPROVED, result.receipt());
		assertNull(result.archiveFailure());
		assertEquals(1, archive.names.size());
		assertEquals("35261234567800019065001000000002000000003-procInutNFe.xml", archive.names.get(0));
		assertEquals(ROOT.resolve("2026").resolve("Janeiro").resolve("Inutilizadas"), archive.directories.get(0));
		assertEquals("<procInutNFe/>", archive.contents.get(0));
	}

	@Test
	void storesNothingWhenRejected() throws Exception {
		VoidingResult result = service(REJECTED).voidRange(REQUEST, ROOT, COLLECTED, List.of());

		assertEquals("241", result.receipt().status());
		assertTrue(archive.names.isEmpty());
	}

	@Test
	void keepsTheApprovalWhenStoringFails() throws Exception {
		archive.failure = new IOException("disco cheio");

		VoidingResult result = service(APPROVED).voidRange(REQUEST, ROOT, COLLECTED, List.of());

		assertEquals("135", result.receipt().protocol());
		assertEquals("disco cheio", result.archiveFailure());
	}

	@Test
	void propagatesAGatewayFailure() {
		VoidingService service = new VoidingService(request -> {
			throw new VoidingException("sem conexao");
		}, archive, CLOCK, FiscalEnvironment.PRODUCTION);

		assertThrows(VoidingException.class, () -> service.voidRange(REQUEST, ROOT, COLLECTED, List.of()));
		assertTrue(archive.names.isEmpty());
	}

	private VoidingService service(VoidingReceipt receipt) {
		return new VoidingService(request -> receipt, archive, CLOCK, FiscalEnvironment.PRODUCTION);
	}

	@Test
	void storesTheApprovedXmlInThePdvFolderAndInTheArchive() throws Exception {
		Path pdv = Path.of("pdv1", "inutilizadas");

		VoidingResult result = service(APPROVED).voidRange(REQUEST, ROOT, COLLECTED, List.of(pdv));

		assertEquals(List.of(), result.pdvFailures());
		assertEquals(List.of(pdv, ROOT.resolve("2026").resolve("Janeiro").resolve("Inutilizadas")), archive.directories);
	}

	@Test
	void reportsAPdvFolderThatFailsAndStillKeepsTheArchiveCopy() throws Exception {
		Path pdv = Path.of("pdv1", "inutilizadas");
		archive.failingDirectory = pdv;

		VoidingResult result = service(APPROVED).voidRange(REQUEST, ROOT, COLLECTED, List.of(pdv));

		assertEquals(1, result.pdvFailures().size());
		assertTrue(result.pdvFailures().get(0).contains("sem acesso"));
		assertNull(result.archiveFailure());
		assertEquals(1, archive.names.size());
	}

	@Test
	void aRejectedOrHomologationVoidingDoesNotGoToThePdv() throws Exception {
		service(REJECTED).voidRange(REQUEST, ROOT, COLLECTED, List.of(Path.of("pdv1")));
		new VoidingService(request -> APPROVED, archive, CLOCK, FiscalEnvironment.HOMOLOGATION)
				.voidRange(REQUEST, ROOT, COLLECTED, List.of(Path.of("pdv1")));

		assertTrue(archive.names.isEmpty());
	}

	@Test
	void aHomologationApprovalIsNotStoredWithTheRealDocuments() throws Exception {
		VoidingResult result = new VoidingService(request -> APPROVED, archive, CLOCK, FiscalEnvironment.HOMOLOGATION)
				.voidRange(REQUEST, ROOT, COLLECTED, List.of());

		assertTrue(result.receipt().approved());
		assertTrue(archive.names.isEmpty());
	}

	private static class FakeArchive implements StubDocumentArchive {

		private final List<String> names = new ArrayList<>();
		private final List<Path> directories = new ArrayList<>();
		private final List<String> contents = new ArrayList<>();
		private IOException failure;
		private Path failingDirectory;

		@Override
		public Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
				String canonicalName, String identity) throws IOException {

			if (failure != null) {
				throw failure;
			}

			if (directory.equals(failingDirectory)) {
				throw new IOException("sem acesso");
			}

			names.add(preferredName);
			directories.add(directory);
			contents.add(new String(content, StandardCharsets.UTF_8));
			return Outcome.COPIED;
		}
	}
}
