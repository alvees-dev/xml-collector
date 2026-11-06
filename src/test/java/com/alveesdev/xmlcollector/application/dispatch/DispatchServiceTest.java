package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Stage;
import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Step;
import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.CloudStorage;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.port.StubArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.StubDirectoryCompressor;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.TestDocuments;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class DispatchServiceTest {

	private static final Consumer<DispatchProgress> NO_PROGRESS = progress -> {
	};

	private static final Path MONTH = Path.of("arquivos", "2026", "Janeiro");
	private static final DispatchMonth JANUARY = DispatchMonth.collected(Path.of("arquivos"), YearMonth.of(2026, 1));
	private static final EmailAccount ACCOUNT = new EmailAccount(
			"smtp.gmail.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-de-teste");

	private static final DispatchRequest REQUEST = new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "Assunto", "", false, ReportOptions.STANDARD);

	private int uploads;
	private final List<String> driveFolders = new ArrayList<>();
	private final List<String> zipNames = new ArrayList<>();
	private final List<EmailMessage> sent = new ArrayList<>();

	private final DirectoryCompressor compressor = (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> {
		zipNames.add(nameOf(zipFile));
		return Optional.of(new DirectoryCompressor.CompressedFile(zipFile, 10));
	};

	@Test
	void namesTheZipWithTheCnpjTheMonthAndTheYear() throws Exception {
		dispatch(Set.of("12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_Janeiro_2026p"), zipNames);
		assertEquals(List.of(MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026p.zip")), sent.get(0).attachments());
	}

	@Test
	void namesEveryCnpjWhenTheMonthHasMoreThanOne() throws Exception {
		dispatch(Set.of("98765432000110", "12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_98765432000110_Janeiro_2026p"), zipNames);
	}

	@Test
	void leavesTheCnpjOutWhenNoneIsFound() throws Exception {
		dispatch(Set.of());

		assertEquals(List.of("ArquivoXML_Janeiro_2026p"), zipNames);
	}

	@Test
	void reportsTheStepsAndSkipsTheDriveWhenTheFileFitsInTheMessage() throws Exception {
		List<DispatchProgress> events = new ArrayList<>();

		service(compressor).dispatch(REQUEST, events::add, () -> false);

		assertEquals(List.of(
				new DispatchProgress(Step.COMPRESSING, Stage.RUNNING),
				new DispatchProgress(Step.COMPRESSING, Stage.DONE),
				new DispatchProgress(Step.UPLOADING, Stage.SKIPPED),
				new DispatchProgress(Step.SENDING, Stage.RUNNING),
				new DispatchProgress(Step.SENDING, Stage.DONE)), events);
	}

	@Test
	void reportsTheDriveWhenTheFileDoesNotFitInTheMessage() throws Exception {
		List<DispatchProgress> events = new ArrayList<>();

		service(largeCompressor()).dispatch(REQUEST, events::add, () -> false);

		assertEquals(List.of(
				new DispatchProgress(Step.COMPRESSING, Stage.RUNNING),
				new DispatchProgress(Step.COMPRESSING, Stage.DONE),
				new DispatchProgress(Step.UPLOADING, Stage.RUNNING),
				new DispatchProgress(Step.UPLOADING, Stage.DONE),
				new DispatchProgress(Step.SENDING, Stage.RUNNING),
				new DispatchProgress(Step.SENDING, Stage.DONE)), events);
	}

	@Test
	void putsTheValuesReportOfTheMonthInTheZip() throws Exception {
		List<DirectoryCompressor.ExtraFile> received = new ArrayList<>();

		ArchivedDocuments documents = new StubArchivedDocuments() {

			@Override
			public Set<String> cnpjsIn(Path directory) {
				return Set.of("12345678000190");
			}

			@Override
			public List<XmlDocument> documentsOfTheCompanyIn(Path directory) {
				return List.of(TestDocuments.invoice(7, LocalDate.of(2026, 1, 15)));
			}
		};

		new DispatchService(documents, (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> {
			received.addAll(extraFiles);
			return Optional.of(new DirectoryCompressor.CompressedFile(zipFile, 10));
		}, (account, message) -> sent.add(message), (accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null))
				.dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals(1, received.size());
		assertEquals("Relatório NFCe Janeiro 2026.txt", received.get(0).name());

		String text = new String(received.get(0).content(), StandardCharsets.UTF_8);
		assertTrue(text.contains("RELATÓRIO NFCe - Janeiro de 2026"), text);
		assertTrue(text.contains("Primeira nota emitida: 7"), text);
	}

	@Test
	void withNothingCheckedTheZipGoesWithoutTheReport() throws Exception {
		List<DirectoryCompressor.ExtraFile> received = new ArrayList<>();
		int[] reads = {0};

		ArchivedDocuments documents = new StubArchivedDocuments() {

			@Override
			public Set<String> cnpjsIn(Path directory) {
				return Set.of("12345678000190");
			}

			@Override
			public List<XmlDocument> documentsOfTheCompanyIn(Path directory) {
				reads[0]++;
				return List.of();
			}
		};

		new DispatchService(documents, (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> {
			received.addAll(extraFiles);
			return Optional.of(new DirectoryCompressor.CompressedFile(zipFile, 10));
		}, (account, message) -> sent.add(message), (accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null))
				.dispatch(new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "Assunto", "", false, new ReportOptions(false, false, false)), NO_PROGRESS, () -> false);

		assertEquals(List.of(), received);
		assertEquals(0, reads[0]);
		assertEquals(1, sent.size());
	}

	@Test
	void thePurchasesAreOnlyReadWhenChecked() throws Exception {
		List<String> report = new ArrayList<>();
		int[] purchaseReads = {0};

		ArchivedDocuments documents = new StubArchivedDocuments() {

			@Override
			public Set<String> cnpjsIn(Path directory) {
				return Set.of("12345678000190");
			}

			@Override
			public List<XmlDocument> purchasesIn(Path directory) {
				purchaseReads[0]++;
				return List.of(TestDocuments.invoice(7, LocalDate.of(2026, 1, 15)));
			}
		};

		DispatchService service = new DispatchService(documents, (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> {
			extraFiles.forEach(file -> report.add(new String(file.content(), StandardCharsets.UTF_8)));
			return Optional.of(new DirectoryCompressor.CompressedFile(zipFile, 10));
		}, (account, message) -> sent.add(message), (accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null));

		service.dispatch(REQUEST, NO_PROGRESS, () -> false);
		assertEquals(0, purchaseReads[0]);

		service.dispatch(new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "Assunto", "", false, new ReportOptions(false, false, true)), NO_PROGRESS, () -> false);
		assertEquals(1, purchaseReads[0]);
		assertTrue(report.get(1).contains("Valor total das compras: R$ 10,50"), report.get(1));
	}

	@Test
	void namesTheDriveFolderWithTheCnpj() throws Exception {
		serviceWith(Set.of("12345678000190"), largeCompressor(), null).dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals(List.of("12345678000190"), driveFolders);
	}

	@Test
	void namesTheDriveFolderWithEveryCnpjOfTheMonth() throws Exception {
		serviceWith(Set.of("98765432000110", "12345678000190"), largeCompressor(), null).dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals(List.of("12345678000190_98765432000110"), driveFolders);
	}

	@Test
	void doesNotUploadWithoutACnpjForTheFolder() {
		assertThrows(DispatchException.class, () -> serviceWith(Set.of(), largeCompressor(), null).dispatch(REQUEST, NO_PROGRESS, () -> false));

		assertEquals(0, uploads);
		assertEquals(List.of(), sent);
	}

	@Test
	void aFileWithoutACnpjStillGoesAttachedWhenItFits() throws Exception {
		serviceWith(Set.of(), compressor, null).dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals(1, sent.size());
	}

	@Test
	void stillSendsTheEmailWhenThePreviousFileCouldNotBeRemoved() throws Exception {
		DispatchResult result = serviceWith(Set.of("12345678000190"), largeCompressor(), "sem acesso").dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals(1, sent.size());
		assertEquals("sem acesso", result.driveCleanupFailure());
	}

	@Test
	void doesNotSendTheEmailWhenCancelledAfterCompressing() {
		assertThrows(CancellationException.class, () -> service(compressor).dispatch(REQUEST, progress -> {
		}, () -> true));

		assertEquals(List.of(), sent);
	}

	@Test
	void doesNotUploadOrSendWhenCancelledBeforeTheDrive() {
		assertThrows(CancellationException.class, () -> service(largeCompressor()).dispatch(REQUEST, progress -> {
		}, () -> true));

		assertEquals(0, uploads);
		assertEquals(List.of(), sent);
	}

	@Test
	void doesNotSendTheEmailWhenCancelledDuringTheDrive() {
		boolean[] cancelled = {false};

		DispatchService service = new DispatchService(
				(StubArchivedDocuments) directory -> Set.of("12345678000190"),
				largeCompressor(),
				(account, message) -> sent.add(message),
				(accountEmail, folderName, files) -> {
					cancelled[0] = true;
					return new CloudStorage.UploadResult("link", null);
				});

		assertThrows(CancellationException.class, () -> service.dispatch(REQUEST, progress -> {
		}, () -> cancelled[0]));
		assertEquals(List.of(), sent);
	}

	@Test
	void doesNotSendOrCompressWithoutASubject() {
		for (String subject : new String[] {"", "   ", null}) {
			DispatchRequest request = new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", subject, "", false, ReportOptions.STANDARD);

			DispatchException error = assertThrows(DispatchException.class, () -> service(compressor).dispatch(request, NO_PROGRESS, () -> false));

			assertEquals("O Assunto não foi informado", error.getMessage());
		}

		assertEquals(List.of(), zipNames);
		assertEquals(List.of(), sent);
	}

	@Test
	void sendsTheSubjectTheUserWrote() throws Exception {
		service(compressor).dispatch(new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "  XML de Janeiro ", "", false, ReportOptions.STANDARD), NO_PROGRESS, () -> false);

		assertEquals("XML de Janeiro", sent.get(0).subject());
	}

	@Test
	void usesTheStandardBodyWhenNoneWasWritten() throws Exception {
		service(compressor).dispatch(REQUEST, NO_PROGRESS, () -> false);

		assertEquals("Olá, prezados.\n\nSegue o arquivo XML.\n\nEmail usado apenas para envio de arquivos. não responder.\n"
				+ "Em caso de dúvidas envie para suporte@unitecautomacao.com.\n\nAtenciosamente,", sent.get(0).body());
	}

	@Test
	void keepsTheBodyTheUserWrote() throws Exception {
		service(compressor).dispatch(new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "Assunto", "Meu texto", false, ReportOptions.STANDARD), NO_PROGRESS, () -> false);

		assertEquals("Meu texto", sent.get(0).body());
	}

	@Test
	void sendsTheExistingZipWithoutCompressingAgain() throws Exception {
		Path existingZip = MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026p.zip");
		List<DispatchProgress> events = new ArrayList<>();

		service(compressorWith(existingZip, 10)).dispatch(reusingTheZip(), events::add, () -> false);

		assertEquals(List.of(), zipNames);
		assertEquals(List.of(existingZip), sent.get(0).attachments());
		assertEquals(new DispatchProgress(Step.COMPRESSING, Stage.SKIPPED), events.get(0));
	}

	@Test
	void sendsALargeExistingZipThroughTheDriveInTheFolderOfTheCnpj() throws Exception {
		Path existingZip = MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026p.zip");

		DispatchResult result = service(compressorWith(existingZip, DispatchService.MAX_MESSAGE_BYTES)).dispatch(reusingTheZip(), NO_PROGRESS, () -> false);

		assertEquals(DispatchResult.Method.DRIVE_LINK, result.method());
		assertEquals(List.of("12345678000190"), driveFolders);
		assertEquals(List.of(), zipNames);
	}

	@Test
	void reportsWhenTheExistingZipIsGone() {
		assertThrows(DispatchException.class, () -> service(compressor).dispatch(reusingTheZip(), NO_PROGRESS, () -> false));

		assertEquals(List.of(), sent);
	}

	@Test
	void onlyCompressingMakesTheZipOfTheMonthWithoutSendingOrUploading() throws Exception {
		DirectoryCompressor.CompressedFile file = service(compressor).compress(JANUARY, ReportOptions.STANDARD);

		assertEquals(List.of("ArquivoXML_12345678000190_Janeiro_2026p"), zipNames);
		assertEquals(MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026p.zip"), file.file());
		assertEquals(List.of(), sent);
		assertEquals(0, uploads);
	}

	@Test
	void onlyCompressingAnEmptyMonthSaysThereIsNothingToCompress() {
		DirectoryCompressor empty = (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> Optional.empty();

		assertThrows(DispatchException.class, () -> service(empty).compress(JANUARY, ReportOptions.STANDARD));
	}

	private static DispatchRequest reusingTheZip() {
		return new DispatchRequest(List.of(JANUARY), ACCOUNT, "destino@exemplo.com", "Assunto", "", true, ReportOptions.STANDARD);
	}

	private DirectoryCompressor compressorWith(Path existingZip, long bytes) {
		return new StubDirectoryCompressor() {

			@Override
			public Optional<CompressedFile> compress(Path directory, Path zipFile, String rootName, List<ExtraFile> extraFiles) {
				zipNames.add(nameOf(zipFile));
				return Optional.empty();
			}

			@Override
			public Optional<CompressedFile> existing(Path zipFolder, String zipLabel) {
				return Optional.of(new CompressedFile(existingZip, bytes));
			}
		};
	}

	private DirectoryCompressor largeCompressor() {
		return (StubDirectoryCompressor) (directory, zipFile, rootName, extraFiles) -> Optional.of(new DirectoryCompressor.CompressedFile(
				zipFile, DispatchService.MAX_MESSAGE_BYTES));
	}

	private static String nameOf(Path zipFile) {
		String name = zipFile.getFileName().toString();
		return name.substring(0, name.length() - ".zip".length());
	}

	private DispatchService service(DirectoryCompressor chosen) {
		return serviceWith(Set.of("12345678000190"), chosen, null);
	}

	private DispatchService serviceWith(Set<String> cnpjs, DirectoryCompressor chosen, String cleanupFailure) {
		return new DispatchService(
				(StubArchivedDocuments) directory -> cnpjs,
				chosen,
				(account, message) -> sent.add(message),
				(accountEmail, folderName, files) -> {
					uploads++;
					driveFolders.add(folderName);
					return new CloudStorage.UploadResult("link", cleanupFailure);
				});
	}

	private void dispatch(Set<String> cnpjs) throws Exception {
		DispatchService service = new DispatchService(
				(StubArchivedDocuments) directory -> cnpjs,
				compressor,
				(account, message) -> sent.add(message),
				(accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null));

		service.dispatch(REQUEST, NO_PROGRESS, () -> false);
	}
}
