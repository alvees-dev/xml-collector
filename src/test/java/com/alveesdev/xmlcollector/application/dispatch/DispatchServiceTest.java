package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Stage;
import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress.Step;
import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.CloudStorage;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.TestDocuments;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class DispatchServiceTest {

	private static final Path MONTH = Path.of("arquivos", "2026", "Janeiro");
	private static final EmailAccount ACCOUNT = new EmailAccount(
			"smtp.gmail.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-de-teste");

	private static final DispatchRequest REQUEST = new DispatchRequest(List.of(MONTH), ACCOUNT, "destino@exemplo.com", "", "");

	private int uploads;
	private final List<String> driveFolders = new ArrayList<>();
	private final List<String> zipNames = new ArrayList<>();
	private final List<EmailMessage> sent = new ArrayList<>();

	private final DirectoryCompressor compressor = (directory, zipName, extraFiles) -> {
		zipNames.add(zipName);
		return Optional.of(new DirectoryCompressor.CompressedFile(directory.resolveSibling(zipName + ".zip"), 10));
	};

	@Test
	void namesTheZipWithTheCnpjTheMonthAndTheYear() throws Exception {
		dispatch(Set.of("12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_Janeiro_2026"), zipNames);
		assertEquals(List.of(MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026.zip")), sent.get(0).attachments());
	}

	@Test
	void namesEveryCnpjWhenTheMonthHasMoreThanOne() throws Exception {
		dispatch(Set.of("98765432000110", "12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_98765432000110_Janeiro_2026"), zipNames);
	}

	@Test
	void leavesTheCnpjOutWhenNoneIsFound() throws Exception {
		dispatch(Set.of());

		assertEquals(List.of("ArquivoXML_Janeiro_2026"), zipNames);
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

		ArchivedDocuments documents = new ArchivedDocuments() {

			@Override
			public Set<String> cnpjsIn(Path directory) {
				return Set.of("12345678000190");
			}

			@Override
			public List<XmlDocument> documentsOfTheCompanyIn(Path directory) {
				return List.of(TestDocuments.invoice(7, LocalDate.of(2026, 1, 15)));
			}
		};

		new DispatchService(documents, (directory, zipName, extraFiles) -> {
			received.addAll(extraFiles);
			return Optional.of(new DirectoryCompressor.CompressedFile(directory.resolveSibling(zipName + ".zip"), 10));
		}, (account, message) -> sent.add(message), (accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null))
				.dispatch(REQUEST);

		assertEquals(1, received.size());
		assertEquals("Relatório de valores.txt", received.get(0).name());

		String text = new String(received.get(0).content(), StandardCharsets.UTF_8);
		assertTrue(text.contains("RELATÓRIO DE VALORES - Janeiro de 2026"), text);
		assertTrue(text.contains("Primeira nota emitida: 7"), text);
	}

	@Test
	void namesTheDriveFolderWithTheCnpj() throws Exception {
		serviceWith(Set.of("12345678000190"), largeCompressor(), null).dispatch(REQUEST);

		assertEquals(List.of("12345678000190"), driveFolders);
	}

	@Test
	void namesTheDriveFolderWithEveryCnpjOfTheMonth() throws Exception {
		serviceWith(Set.of("98765432000110", "12345678000190"), largeCompressor(), null).dispatch(REQUEST);

		assertEquals(List.of("12345678000190_98765432000110"), driveFolders);
	}

	@Test
	void doesNotUploadWithoutACnpjForTheFolder() {
		assertThrows(DispatchException.class, () -> serviceWith(Set.of(), largeCompressor(), null).dispatch(REQUEST));

		assertEquals(0, uploads);
		assertEquals(List.of(), sent);
	}

	@Test
	void aFileWithoutACnpjStillGoesAttachedWhenItFits() throws Exception {
		serviceWith(Set.of(), compressor, null).dispatch(REQUEST);

		assertEquals(1, sent.size());
	}

	@Test
	void stillSendsTheEmailWhenThePreviousFileCouldNotBeRemoved() throws Exception {
		DispatchResult result = serviceWith(Set.of("12345678000190"), largeCompressor(), "sem acesso").dispatch(REQUEST);

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
				directory -> Set.of("12345678000190"),
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

	private DirectoryCompressor largeCompressor() {
		return (directory, zipName, extraFiles) -> Optional.of(new DirectoryCompressor.CompressedFile(
				directory.resolveSibling(zipName + ".zip"), DispatchService.MAX_MESSAGE_BYTES));
	}

	private DispatchService service(DirectoryCompressor chosen) {
		return serviceWith(Set.of("12345678000190"), chosen, null);
	}

	private DispatchService serviceWith(Set<String> cnpjs, DirectoryCompressor chosen, String cleanupFailure) {
		return new DispatchService(
				directory -> cnpjs,
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
				directory -> cnpjs,
				compressor,
				(account, message) -> sent.add(message),
				(accountEmail, folderName, files) -> new CloudStorage.UploadResult("link", null));

		service.dispatch(new DispatchRequest(List.of(MONTH), ACCOUNT, "destino@exemplo.com", "", ""));
	}
}
