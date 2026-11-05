package com.alveesdev.xmlcollector.application.voiding;

import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult.SourceResult;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.StubDocumentArchive;
import com.alveesdev.xmlcollector.application.port.StubIssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

class VoidingPreparationTest {

	private static final String CNPJ = "12345678000190";
	private static final LocalDate DAY = LocalDate.of(2026, 2, 15);
	private static final DateRange FEBRUARY = new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
	private static final NumberRange GAP = new NumberRange(5, 5);
	private static final Map<Integer, List<NumberRange>> WANTED = Map.of(1, List.of(GAP));
	private static final List<String> PDVS = List.of("pdv1");
	private static final CertificateSettings CERTIFICATE = new CertificateSettings("a.pfx", "x", FiscalEnvironment.HOMOLOGATION);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-05T12:00:00Z"), ZoneOffset.UTC);
	private static final Path ARCHIVE = Path.of("arquivo");

	// The order of the checks is part of the safety, so every step is recorded here.
	private final List<String> steps = new ArrayList<>();
	private final MissingNumbersSession session = new MissingNumbersSession();

	private String certificateCnpj = CNPJ;
	private Set<Integer> alreadyIssued = Set.of();
	private boolean folderAcceptsWriting = true;
	private boolean archiveAcceptsWriting = true;

	private final IssuedNumberRepository history = new StubIssuedNumberRepository() {

		@Override
		public void initialize() {
		}

		@Override
		public void saveAll(Collection<IssuedNumber> numbers) {
		}

		@Override
		public Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) {
			steps.add("history");
			return alreadyIssued;
		}
	};

	private final VoidingGatewayFactory gateways = settings -> {
		steps.add("certificate");
		return new VoidingGatewayFactory.Connection(request -> {
			throw new AssertionError("preparing must not send anything");
		}, new VoidingGatewayFactory.CertificateInfo(certificateCnpj, CLOCK.instant()));
	};

	private final DocumentArchive archive = new StubDocumentArchive() {

		@Override
		public Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName, String canonicalName, String identity) {
			throw new AssertionError("preparing must not store anything");
		}

		@Override
		public void requireWritable(Path directory) throws IOException {
			steps.add(directory.startsWith(ARCHIVE) ? "archive" : "folder");

			if (directory.startsWith(ARCHIVE) && !archiveAcceptsWriting) {
				throw new IOException("disco cheio");
			}

			if (!folderAcceptsWriting) {
				throw new IOException("sem acesso");
			}
		}
	};

	private VoidingPreparation preparation;

	@BeforeEach
	void collect() {
		preparation = new VoidingPreparation(new VoidingPlanner(history), new VoidingBatch(gateways, archive, CLOCK));

		session.registerCollection(
				new CollectionRequest(PDVS, DocumentCategory.NFCE, FEBRUARY, OrganizeMode.SINGLE_FOLDER, Path.of("arquivo"), List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER),
				new CollectionResult(List.of(new SourceResult(0, List.of(DocumentEntry.of(invoice(1, 4, DAY), false, "4.xml")))),
						new TreeMap<>(WANTED), new CollectionSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()), List.of(), null, Set.of()));
	}

	@Test
	void checksInOrderAndReturnsThePlan() throws VoidingException {
		VoidingPreparation.Plan plan = preparation.prepare(session, WANTED, CERTIFICATE, PDVS, "inutilizadas/{AAAAMM}", ARCHIVE);

		assertEquals(List.of("history", "certificate", "folder", "archive"), steps);
		assertEquals(1, plan.requests().size());
		assertEquals(GAP, plan.requests().get(0).range());
		assertEquals(Map.of(1, List.of(Path.of("pdv1", "inutilizadas", "202602"))), plan.pdvFolders());
	}

	@Test
	void aNumberOutOfTheListStopsBeforeTouchingAnything() {
		Map<Integer, List<NumberRange>> outOfTheList = Map.of(1, List.of(new NumberRange(9, 9)));

		assertThrows(VoidingException.class, () -> preparation.prepare(session, outOfTheList, CERTIFICATE, PDVS, "inutilizadas", ARCHIVE));

		assertEquals(List.of(), steps);
	}

	@Test
	void aNumberInTheHistoryStopsBeforeOpeningTheCertificate() {
		alreadyIssued = Set.of(5);

		assertThrows(VoidingException.class, () -> preparation.prepare(session, WANTED, CERTIFICATE, PDVS, "inutilizadas", ARCHIVE));

		assertEquals(List.of("history"), steps);
	}

	@Test
	void aCertificateOfAnotherCompanyStopsBeforeTestingTheFolders() {
		certificateCnpj = "98765432000110";

		VoidingException error = assertThrows(VoidingException.class, () -> preparation.prepare(session, WANTED, CERTIFICATE, PDVS, "inutilizadas", ARCHIVE));

		assertTrue(error.getMessage().contains(certificateCnpj), error.getMessage());
		assertEquals(List.of("history", "certificate"), steps);
	}

	@Test
	void aModelWithoutVoidingFolderStopsBeforeTestingTheFolders() {
		assertThrows(VoidingException.class, () -> preparation.prepare(session, WANTED, CERTIFICATE, PDVS, " ", ARCHIVE));

		assertEquals(List.of("history", "certificate"), steps);
	}

	@Test
	void aFolderThatRefusesWritingStopsThePreparation() {
		folderAcceptsWriting = false;

		VoidingException error = assertThrows(VoidingException.class, () -> preparation.prepare(session, WANTED, CERTIFICATE, PDVS, "inutilizadas", ARCHIVE));

		assertTrue(error.getMessage().contains("Nada foi enviado"), error.getMessage());
	}

	@Test
	void anArchiveThatRefusesWritingStopsThePreparation() {
		archiveAcceptsWriting = false;

		VoidingException error = assertThrows(VoidingException.class, () -> preparation.prepare(session, WANTED, CERTIFICATE, PDVS, "inutilizadas", ARCHIVE));

		assertTrue(error.getMessage().contains("pasta de arquivos"), error.getMessage());
		assertTrue(error.getMessage().contains("Nada foi enviado"), error.getMessage());
	}

}
