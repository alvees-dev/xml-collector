package com.alveesdev.xmlcollector.endtoend;

import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.CNPJ;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.keyOf;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.writeInvoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.LogCapture;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingPlanner;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.persistence.SqliteIssuedNumberRepository;
import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;

// Everything real but the Sefaz, which answers from the test: nothing leaves the computer.
class VoidingFlowTest {

	private static final LocalDate COLLECTED_DAY = LocalDate.of(2026, 1, 15);
	private static final LocalDate LAST_COLLECTED_DAY = LocalDate.of(2026, 1, 31);
	private static final LocalDate VOIDED_DAY = LocalDate.of(2026, 2, 5);
	private static final DateRange JANUARY = new DateRange(LocalDate.of(2026, 1, 1), LAST_COLLECTED_DAY);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-05T15:00:00Z"), ZoneOffset.UTC);
	private static final String VOIDING_FOLDER = "Inutilizadas/" + SearchFolderPattern.CNPJ + "/" + SearchFolderPattern.YEAR_MONTH;
	private static final CertificateSettings CERTIFICATE = new CertificateSettings("empresa.pfx", "senha-de-teste", FiscalEnvironment.PRODUCTION);

	@TempDir
	Path root;

	private Path pdv;
	private Path archiveRoot;
	private SqliteIssuedNumberRepository history;
	private CollectionService collection;
	private VoidingBatch batch;

	private final List<VoidingRequest> sentToSefaz = new ArrayList<>();

	@BeforeEach
	void createTheEnvironment() {
		pdv = root.resolve("pdv1");
		archiveRoot = root.resolve("Arquivos_XML");
		history = new SqliteIssuedNumberRepository(root.resolve("Database").resolve("coletor.db"));

		DocumentReader reader = new FiscalXmlReader();
		DocumentArchive archive = new XmlArchive(reader);

		collection = new CollectionService(new XmlFileScanner(), reader, archive, history);
		batch = new VoidingBatch(sefazThatApproves(), archive, CLOCK);
	}

	@Test
	void anApprovedNumberIsSavedAndStopsBeingMissing() throws Exception {
		writeInvoice(pdv, 1, 1, COLLECTED_DAY);
		writeInvoice(pdv, 1, 2, COLLECTED_DAY);
		writeInvoice(pdv, 1, 4, COLLECTED_DAY);

		CollectionResult collected = collect();
		NumberRange gap = new NumberRange(3, 3);
		assertEquals(List.of(gap), collected.missingBySeries().get(1));
		assertEquals(List.of(), VoidingPlanner.problemsOf(collected));

		List<VoidingRequest> requests = new VoidingPlanner(history).plan(Map.of(1, List.of(gap)), collected.missingBySeries(), collected.ownersBySeries());
		Map<Integer, List<Path>> pdvFolders = VoidingPlanner.pdvFolders(requests, List.of(pdv.toString()), collected.sourcesBySeries(), VOIDING_FOLDER,
				YearMonth.from(LAST_COLLECTED_DAY));

		List<VoidingOutcome> outcomes = batch.run(requests, CERTIFICATE, archiveRoot, LAST_COLLECTED_DAY, pdvFolders, progress -> {
		}, () -> false);

		assertEquals(requests, sentToSefaz);
		assertEquals(List.of(Stage.APPROVED), outcomes.stream().map(VoidingOutcome::stage).toList());
		assertEquals(List.of(), outcomes.get(0).pdvFailures());

		String fileName = ArchiveLayout.voidingFileName(requests.get(0).id());
		assertTrue(Files.isRegularFile(pdv.resolve("Inutilizadas").resolve(CNPJ).resolve("202601").resolve(fileName)));
		assertTrue(Files.isRegularFile(ArchiveLayout.voidingDirectoryFor(archiveRoot, LAST_COLLECTED_DAY).resolve(fileName)));

		assertEquals(List.of(), collect().missingBySeries().getOrDefault(1, List.of()));
	}

	@Test
	void recordsWhatHappenedToEachRangeWithoutThePassword() throws Exception {
		writeInvoice(pdv, 1, 1, COLLECTED_DAY);
		writeInvoice(pdv, 1, 3, COLLECTED_DAY);

		CollectionResult collected = collect();
		NumberRange gap = new NumberRange(2, 2);
		List<VoidingRequest> requests = new VoidingPlanner(history).plan(Map.of(1, List.of(gap)), collected.missingBySeries(), collected.ownersBySeries());

		try (LogCapture log = new LogCapture(VoidingBatch.class)) {
			batch.run(requests, CERTIFICATE, archiveRoot, LAST_COLLECTED_DAY, Map.of(), progress -> {
			}, () -> false);

			assertTrue(log.has(Level.INFO, requests.get(0).id()), "the range must be recorded");
			assertTrue(log.has(Level.INFO, "APPROVED"), "the answer must be recorded");
			assertTrue(log.records().stream().noneMatch(entry -> entry.getMessage().contains(CERTIFICATE.password())));
		}
	}

	@Test
	void aNumberAlreadyInTheHistoryIsRefusedEvenIfItsFileDisappeared() throws IOException {
		writeInvoice(pdv, 1, 1, COLLECTED_DAY);
		writeInvoice(pdv, 1, 2, COLLECTED_DAY);
		writeInvoice(pdv, 1, 3, COLLECTED_DAY);
		collect();

		Files.delete(pdv.resolve(keyOf(1, 2, COLLECTED_DAY) + "-nfe.xml"));
		CollectionResult again = collect();
		NumberRange gap = new NumberRange(2, 2);
		assertEquals(List.of(gap), again.missingBySeries().get(1));

		VoidingException refused = assertThrows(VoidingException.class,
				() -> new VoidingPlanner(history).plan(Map.of(1, List.of(gap)), again.missingBySeries(), again.ownersBySeries()));

		assertTrue(refused.getMessage().contains("histórico"), refused.getMessage());
		assertEquals(List.of(), sentToSefaz);
	}

	private CollectionResult collect() {
		CollectionRequest request = new CollectionRequest(List.of(pdv.toString()), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER,
				archiveRoot, List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);

		return collection.collect(request, progress -> {
		}, () -> false);
	}

	private VoidingGatewayFactory sefazThatApproves() {
		VoidingGatewayFactory.CertificateInfo certificate = new VoidingGatewayFactory.CertificateInfo(CNPJ, Instant.parse("2027-01-01T00:00:00Z"));

		return settings -> new VoidingGatewayFactory.Connection(request -> {
			sentToSefaz.add(request);

			return new VoidingReceipt("102", "Inutilizacao de numero homologado", "135260000000001", VOIDED_DAY + "T12:00:00-03:00",
					SyntheticXml.voiding(request.series(), request.range(), VOIDED_DAY));
		}, certificate);
	}
}
