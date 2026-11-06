package com.alveesdev.xmlcollector.endtoend;

import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.CNPJ;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.NFCE;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.writeCancellation;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.writeInvoice;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.writeNfe;
import static com.alveesdev.xmlcollector.endtoend.SyntheticXml.writeVoiding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionSummary;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.dispatch.DispatchMonth;
import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.dispatch.ReportOptions;
import com.alveesdev.xmlcollector.application.dispatch.ValuesReportText;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.infra.archive.MonthZipArchiver;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.archive.XmlArchivedDocuments;
import com.alveesdev.xmlcollector.infra.persistence.SqliteIssuedNumberRepository;
import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;

// Everything real but the two services outside the computer, the email and the Google Drive.
class CollectionAndDispatchFlowTest {

	private static final LocalDate JANUARY_DAY = LocalDate.of(2026, 1, 15);
	private static final DateRange JANUARY = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
	private static final DateRange FEBRUARY = new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
	private static final int STORED_FILES = 8;

	@TempDir
	Path root;

	private Path firstPdv;
	private Path secondPdv;
	private Path archiveRoot;
	private SqliteIssuedNumberRepository history;
	private CollectionService collection;

	private final DocumentReader reader = new FiscalXmlReader();
	private final List<EmailMessage> emails = new ArrayList<>();

	@BeforeEach
	void createTheEnvironment() {
		firstPdv = root.resolve("pdv1");
		secondPdv = root.resolve("pdv2");
		archiveRoot = root.resolve("Arquivos_XML");
		history = new SqliteIssuedNumberRepository(root.resolve("Database").resolve("coletor.db"));
		collection = new CollectionService(new XmlFileScanner(), reader, new XmlArchive(reader), history);
	}

	@Test
	void organizesTheFilesFindsTheGapAndKeepsTheHistory() throws IOException {
		writeAMonthOfNotes();

		CollectionResult result = collect(JANUARY);

		assertEquals(List.of(), result.failures());
		assertNull(result.historyFailure());

		CollectionSummary summary = result.summary();
		assertEquals(STORED_FILES, summary.copied());
		assertEquals(1, summary.errors());
		assertEquals(1, summary.ignored());
		assertEquals(List.of(), summary.copyFailures());

		assertEquals(List.of(new NumberRange(5, 5)), result.missingBySeries().get(1));
		assertEquals(List.of(), result.missingBySeries().getOrDefault(2, List.of()));

		assertEquals(Map.of(1, DocumentStatus.AUTHORIZED, 2, DocumentStatus.CANCELLED, 3, DocumentStatus.AUTHORIZED, 6, DocumentStatus.AUTHORIZED),
				statusOfSeries(1, result));

		Path month = ArchiveLayout.monthDirectory(archiveRoot, JANUARY_DAY);
		assertEquals(STORED_FILES, filesIn(month).size());
		assertTrue(Files.isRegularFile(month.resolve("NFCe").resolve(SyntheticXml.keyOf(1, 1, JANUARY_DAY) + "-nfe.xml")));
		assertEquals(1, filesIn(month.resolve("Inutilizadas")).size());

		assertEquals(Set.of(1, 2, 3, 6), history.issuedIn(CNPJ, NFCE, 1, new NumberRange(1, 6)));
		assertEquals(Set.of(1, 2), history.issuedIn(CNPJ, NFCE, 2, new NumberRange(1, 6)));
	}

	@Test
	void collectingAgainDoesNotCopyOrChangeAnything() throws IOException {
		writeAMonthOfNotes();
		collect(JANUARY);

		CollectionResult again = collect(JANUARY);

		assertEquals(0, again.summary().copied());
		assertEquals(0, again.summary().replaced());
		assertEquals(STORED_FILES, again.summary().alreadyExisting());
		assertEquals(List.of(new NumberRange(5, 5)), again.missingBySeries().get(1));
		assertEquals(STORED_FILES, filesIn(ArchiveLayout.monthDirectory(archiveRoot, JANUARY_DAY)).size());
	}

	@Test
	void readingFindsTheGapsLikeACollectionWithoutCopying() throws IOException {
		writeAMonthOfNotes();

		CollectionRequest request = new CollectionRequest(List.of(firstPdv.toString()), DocumentCategory.NFCE, JANUARY,
				OrganizeMode.SINGLE_FOLDER, archiveRoot, List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);

		CollectionResult read = collection.read(request, progress -> {
		}, () -> false);

		assertEquals(Map.of(1, DocumentStatus.AUTHORIZED, 2, DocumentStatus.CANCELLED, 3, DocumentStatus.AUTHORIZED, 6, DocumentStatus.AUTHORIZED),
				statusOfSeries(1, read));
		assertEquals(List.of(new NumberRange(5, 5)), read.missingBySeries().get(1));
		assertEquals(0, read.summary().copied());
		assertFalse(Files.exists(archiveRoot), "nothing may be written in the archive");
		assertEquals(Set.of(1, 2, 3, 6), history.issuedIn(CNPJ, NFCE, 1, new NumberRange(1, 6)));
	}

	@Test
	void sendsTheCollectedMonthWithTheReportOfValues() throws Exception {
		writeAMonthOfNotes();
		collect(JANUARY);

		DispatchResult sent = dispatchService().dispatch(dispatchRequest(), progress -> {
		}, () -> false);

		assertEquals(DispatchResult.Method.ATTACHED, sent.method());

		EmailMessage email = emails.get(0);
		assertEquals("contador@exemplo.com", email.recipient());
		assertEquals(1, email.attachments().size());

		Path zip = email.attachments().get(0);
		assertEquals("ArquivoXML_" + CNPJ + "_Janeiro_2026p.zip", zip.getFileName().toString());

		try (ZipFile file = new ZipFile(zip.toFile())) {
			assertEquals(STORED_FILES, file.stream().filter(entry -> entry.getName().endsWith(".xml")).count());

			String report = textOf(file, "Janeiro_2026/" + ValuesReportText.FILE_NAME);

			assertTrue(report.contains("Notas canceladas: 1 nota\r\n"), report);
			assertTrue(report.contains("Notas inutilizadas: 1 nota\r\n"), report);
			assertTrue(report.contains("Última nota emitida: 6"), report);
			assertTrue(report.contains("Total de vendas: 5 notas"), report);
			assertTrue(report.contains("Valor total das vendas: R$ 52,50"), report);
		}
	}

	@Test
	void theCheckedKindsChooseWhatGoesInTheReport() throws Exception {
		writeAMonthOfNotes();
		collect(JANUARY);
		writeNfeOfTheMonth();

		dispatchService().dispatch(dispatchRequestWith(new ReportOptions(true, true, true)), progress -> {
		}, () -> false);

		try (ZipFile file = new ZipFile(emails.get(0).attachments().get(0).toFile())) {
			String report = textOf(file, "Janeiro_2026/" + ValuesReportText.FILE_NAME);

			assertTrue(report.contains("- NFC-e - Série 1"), report);
			assertTrue(report.contains("NF-e - VENDAS\r\nValor total das vendas: R$ 21,00"), report);
			assertTrue(report.contains("NF-e - COMPRAS\r\nValor total das compras: R$ 31,50"), report);
			assertTrue(report.contains("TOTAL DO MÊS\r\nTotal de vendas: 7 notas\r\nValor total das vendas: R$ 73,50"), report);
			assertFalse(report.contains("- NF-e - Série"), report);
		}
	}

	@Test
	void nothingCheckedSendsTheFilesWithoutTheReport() throws Exception {
		writeAMonthOfNotes();
		collect(JANUARY);

		dispatchService().dispatch(dispatchRequestWith(new ReportOptions(false, false, false)), progress -> {
		}, () -> false);

		try (ZipFile file = new ZipFile(emails.get(0).attachments().get(0).toFile())) {
			assertNull(file.getEntry("Janeiro_2026/" + ValuesReportText.FILE_NAME));
			assertEquals(STORED_FILES, file.stream().filter(entry -> entry.getName().endsWith(".xml")).count());
		}
	}

	@Test
	void theNextMonthFindsTheGapAgainstTheHistory() throws IOException {
		writeInvoice(firstPdv, 1, 1, JANUARY_DAY);
		writeInvoice(firstPdv, 1, 2, JANUARY_DAY);
		collect(JANUARY);

		LocalDate february = LocalDate.of(2026, 2, 10);
		writeInvoice(firstPdv, 1, 5, february);
		writeInvoice(firstPdv, 1, 6, february);

		CollectionResult next = collect(FEBRUARY);

		assertEquals(List.of(new NumberRange(3, 4)), next.missingBySeries().get(1));
	}

	private void writeAMonthOfNotes() throws IOException {
		writeInvoice(firstPdv, 1, 1, JANUARY_DAY);
		writeInvoice(firstPdv, 1, 2, JANUARY_DAY);
		writeInvoice(firstPdv, 1, 3, JANUARY_DAY);
		writeInvoice(firstPdv, 1, 6, JANUARY_DAY);
		writeCancellation(firstPdv, 1, 2, JANUARY_DAY);
		writeVoiding(firstPdv, 1, new NumberRange(4, 4), JANUARY_DAY);
		Files.writeString(firstPdv.resolve("quebrado.xml"), "<nfeProc><NFe>");
		Files.writeString(firstPdv.resolve("configuracao.xml"), "<configuracao/>");
		Files.writeString(firstPdv.resolve("leia-me.txt"), "nao e um xml");

		writeInvoice(secondPdv, 2, 1, JANUARY_DAY);
		writeInvoice(secondPdv, 2, 2, JANUARY_DAY);
	}

	private void writeNfeOfTheMonth() throws IOException {
		Path month = ArchiveLayout.monthDirectory(archiveRoot, JANUARY_DAY);

		writeNfe(month.resolve("NFe_Vendas"), CNPJ, 1, JANUARY_DAY);
		writeNfe(month.resolve("NFe_Vendas"), CNPJ, 2, JANUARY_DAY);
		writeNfe(month.resolve("NFe_Compras"), "98765432000110", 7, JANUARY_DAY);
		writeNfe(month.resolve("NFe_Compras"), "98765432000110", 8, JANUARY_DAY);
		writeNfe(month.resolve("NFe_Compras"), "11222333000181", 9, JANUARY_DAY);
	}

	private DispatchRequest dispatchRequestWith(ReportOptions reports) {
		DispatchRequest request = dispatchRequest();

		return new DispatchRequest(request.months(), request.account(), request.recipient(), request.subject(), request.body(), false, reports);
	}

	private CollectionResult collect(DateRange period) {
		CollectionRequest request = new CollectionRequest(List.of(firstPdv.toString(), secondPdv.toString()), DocumentCategory.NFCE, period,
				OrganizeMode.SINGLE_FOLDER, archiveRoot, List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);

		return collection.collect(request, progress -> {
		}, () -> false);
	}

	@Test
	void sendsTheMonthOfTheServerWithoutWritingInIt() throws Exception {
		Path server = root.resolve("servidor");
		Path serverMonth = server.resolve("2026").resolve("01");
		writeInvoice(serverMonth, 1, 1, JANUARY_DAY);
		writeInvoice(serverMonth, 1, 2, JANUARY_DAY);
		writeInvoice(server.resolve("2026").resolve("02"), 1, 3, LocalDate.of(2026, 2, 10));

		Path found = collection.monthFolderOf(server.toString(), List.of("{AAAA}/{MM}"), YearMonth.of(2026, 1)).orElseThrow();
		assertEquals(serverMonth, found);

		EmailAccount account = new EmailAccount("smtp.exemplo.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-de-teste");
		DispatchRequest request = new DispatchRequest(List.of(DispatchMonth.server(found, archiveRoot, YearMonth.of(2026, 1))), account,
				"contador@exemplo.com", "XML de janeiro", "Segue o arquivo.", false, ReportOptions.STANDARD);

		dispatchService().dispatch(request, progress -> {
		}, () -> false);

		Path zip = emails.get(0).attachments().get(0);
		assertEquals(archiveRoot.resolve("2026").resolve("ArquivoXML_" + CNPJ + "_Janeiro_2026s.zip"), zip);
		assertEquals(2, filesIn(serverMonth).size());
		assertEquals(3, filesIn(server).size());

		try (ZipFile file = new ZipFile(zip.toFile())) {
			assertTrue(textOf(file, "Janeiro_2026/" + ValuesReportText.FILE_NAME).contains("Total de vendas: 2 notas"));
		}
	}

	private DispatchService dispatchService() {
		return new DispatchService(new XmlArchivedDocuments(reader), new MonthZipArchiver(), (account, message) -> emails.add(message),
				(account, folder, files) -> {
					throw new AssertionError("a small month goes as an attachment");
				});
	}

	private DispatchRequest dispatchRequest() {
		EmailAccount account = new EmailAccount("smtp.exemplo.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-de-teste");

		return new DispatchRequest(List.of(DispatchMonth.collected(archiveRoot, YearMonth.from(JANUARY_DAY))), account, "contador@exemplo.com", "XML de janeiro",
				"Segue o arquivo.", false, ReportOptions.STANDARD);
	}

	private static Map<Integer, DocumentStatus> statusOfSeries(int series, CollectionResult result) {
		return result.entries().stream()
				.filter(entry -> entry.invoice() != null && entry.invoice().series() == series)
				.collect(Collectors.toMap(entry -> entry.invoice().number(), DocumentEntry::status));
	}

	private static List<Path> filesIn(Path directory) throws IOException {
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile).toList();
		}
	}

	private static String textOf(ZipFile file, String name) throws IOException {
		ZipEntry entry = file.getEntry(name);

		assertTrue(entry != null, "missing in the zip: " + name);

		return new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
	}

	@Test
	void aCompanyWithAnAlphanumericCnpjIsCollectedAndSent() throws Exception {
		String cnpj = "12ABC34501DE35";
		writeNfe(firstPdv, cnpj, 1, JANUARY_DAY);
		writeNfe(firstPdv, cnpj, 2, JANUARY_DAY);

		CollectionResult result = collection.collect(new CollectionRequest(List.of(firstPdv.toString()), DocumentCategory.NFE_SALES, JANUARY,
				OrganizeMode.SINGLE_FOLDER, archiveRoot, List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER), progress -> {
				}, () -> false);

		assertEquals(0, result.summary().errors());
		assertEquals(2, result.summary().copied());

		dispatchService().dispatch(dispatchRequestWith(new ReportOptions(false, true, false)), progress -> {
		}, () -> false);

		Path zip = emails.get(0).attachments().get(0);
		assertEquals("ArquivoXML_" + cnpj + "_Janeiro_2026p.zip", zip.getFileName().toString());

		try (ZipFile file = new ZipFile(zip.toFile())) {
			assertTrue(textOf(file, "Janeiro_2026/" + ValuesReportText.FILE_NAME).contains("Valor total das vendas: R$ 21,00"));
		}
	}


	@Test
	void aFileMadeToLookLikeAVoidingCannotWriteOutsideTheArchive() throws IOException {
		writeInvoice(firstPdv, 1, 1, JANUARY_DAY);

		String malicious = SyntheticXml.voiding(1, new NumberRange(5, 5), JANUARY_DAY).replace(CNPJ, "..\\..\\..\\..\\fora");
		Files.writeString(firstPdv.resolve("inutilizacao.xml"), malicious);

		CollectionResult result = collect(JANUARY);

		assertEquals(1, result.summary().errors());
		assertEquals(1, result.summary().copied());
		assertEquals(List.of(), filesEndingWith(root, "-procInutNFe.xml"));
	}

	@Test
	void aFileWithAnInvalidCharacterInTheCnpjDoesNotStopTheCollection() throws IOException {
		writeInvoice(firstPdv, 1, 1, JANUARY_DAY);
		Files.writeString(firstPdv.resolve("inutilizacao.xml"), SyntheticXml.voiding(1, new NumberRange(5, 5), JANUARY_DAY).replace(CNPJ, "1234567800:190"));

		CollectionResult result = collect(JANUARY);

		assertEquals(1, result.summary().errors());
		assertEquals(1, result.summary().copied());
	}

	private static List<Path> filesEndingWith(Path directory, String suffix) throws IOException {
		return filesIn(directory).stream().filter(file -> file.getFileName().toString().endsWith(suffix)).toList();
	}

}
