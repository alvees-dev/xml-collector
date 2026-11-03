package com.alveesdev.xmlcollector.application.collection;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.SourceFiles;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class CollectionServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2026, 1, 15);
	private static final LocalDate AFTER_PERIOD = LocalDate.of(2026, 2, 15);
	private static final DateRange JANUARY = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
	private static final Path ARCHIVE = Path.of("archive");

	private final FakeSources sources = new FakeSources();
	private final FakeArchive archive = new FakeArchive();
	private final FakeIssuedNumbers issuedNumbers = new FakeIssuedNumbers();
	private final CollectionService service = new CollectionService(sources, sources, archive, issuedNumbers, 2);

	@Test
	void marksInvoiceAsCancelledWhenTheEventIsInTheSameSource() {
		sources.add("pdv1", "nota.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "evento.xml", cancellation(1, IN_PERIOD));

		assertEquals(DocumentStatus.CANCELLED, onlyEntry(collect("pdv1")).status());
	}

	@Test
	void marksInvoiceAsCancelledWhenTheEventIsInALaterSource() {
		sources.add("pdv1", "nota.xml", invoice(1, IN_PERIOD));
		sources.add("pdv2", "evento.xml", cancellation(1, IN_PERIOD));

		assertEquals(DocumentStatus.CANCELLED, onlyEntry(collect("pdv1", "pdv2")).status());
	}

	@Test
	void marksInvoiceAsCancelledWhenTheEventIsInAnEarlierSource() {
		sources.add("pdv1", "evento.xml", cancellation(1, IN_PERIOD));
		sources.add("pdv2", "nota.xml", invoice(1, IN_PERIOD));

		assertEquals(DocumentStatus.CANCELLED, onlyEntry(collect("pdv1", "pdv2")).status());
	}

	@Test
	void keepsResultsPerSourceInRequestOrder() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, IN_PERIOD));

		CollectionResult result = collect("pdv1", "pdv2");

		assertEquals(List.of(0, 1), result.sources().stream().map(CollectionResult.SourceResult::sourceIndex).toList());
		assertEquals("a.xml", result.sources().get(0).entries().get(0).fileName());
		assertEquals("b.xml", result.sources().get(1).entries().get(0).fileName());
	}

	@Test
	void reportsMissingNumbersPerSeries() {
		sources.add("pdv1", "a.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv1", "b.xml", invoice(1, 4, IN_PERIOD));
		sources.add("pdv2", "c.xml", invoice(2, 1, IN_PERIOD));
		sources.add("pdv2", "d.xml", invoice(2, 2, IN_PERIOD));

		CollectionResult result = collect("pdv1", "pdv2");

		assertEquals(Map.of(1, List.of(new NumberRange(2, 3)), 2, List.of()), result.missingBySeries());
	}

	@Test
	void anInvoiceInTheWrongFolderIsNotReportedAsMissing() {
		sources.add("pdv1", "a.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv1", "c.xml", invoice(1, 3, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(1, 2, IN_PERIOD));

		assertEquals(Map.of(1, List.of()), collect("pdv1", "pdv2").missingBySeries());
	}

	@Test
	void countsEachKindOfFileInTheSummary() {
		sources.add("pdv1", "nota.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "copia.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "fora.xml", invoice(2, AFTER_PERIOD));
		sources.add("pdv1", "outro.xml", new XmlDocument.Ignored("not an NFCe"));
		sources.add("pdv1", "ruim.xml", new XmlDocument.Unreadable("broken"));

		CollectionSummary summary = collect("pdv1").summary();

		assertEquals(1, summary.copied());
		assertEquals(1, summary.duplicates());
		assertEquals(1, summary.outOfPeriod());
		assertEquals(1, summary.ignored());
		assertEquals(1, summary.errors());
	}

	@Test
	void storesAnInvoiceFoundInTwoSourcesOnlyOnce() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv2", "a.xml", invoice(1, IN_PERIOD));

		CollectionSummary summary = collect("pdv1", "pdv2").summary();

		assertEquals(1, archive.stored.size());
		assertEquals(1, summary.duplicates());
	}

	@Test
	void storesAnInvoiceUnderItsOriginalNameWithTheKeyAsAlternative() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		collect("pdv1");

		Stored stored = archive.stored.get(0);
		String key = invoice(1, IN_PERIOD).key().value();

		assertEquals("a.xml", stored.preferredName());
		assertEquals(key + ".xml", stored.canonicalName());
		assertEquals(key, stored.identity());
		assertEquals(ARCHIVE.resolve("Janeiro_2026").resolve("NFCe"), stored.directory());
	}

	@Test
	void storesACancellationUnderTheEventId() {
		XmlDocument.CancelEvent event = cancellation(1, IN_PERIOD);
		sources.add("pdv1", "evento.xml", event);

		collect("pdv1");

		Stored stored = archive.stored.get(0);

		assertEquals(event.eventId() + ".xml", stored.preferredName());
		assertEquals(event.eventId() + ".xml", stored.canonicalName());
		assertEquals(event.eventId(), stored.identity());
	}

	@Test
	void reportsAFailedCopyAndKeepsGoing() {
		sources.add("pdv1", "falha.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "ok.xml", invoice(2, IN_PERIOD));
		archive.failOn("falha.xml");

		CollectionSummary summary = collect("pdv1").summary();

		assertEquals(1, summary.copied());
		assertEquals(List.of(new CollectionSummary.CopyFailure("falha.xml", "disk full")), summary.copyFailures());
	}

	@Test
	void reportsBlankAndInaccessibleSourcesWithoutStopping() {
		sources.add("pdv3", "a.xml", invoice(1, IN_PERIOD));

		CollectionResult result = collect("  ", "inexistente", "pdv3");

		assertEquals(
				List.of(SourceFailure.Reason.NOT_INFORMED, SourceFailure.Reason.INACCESSIBLE),
				result.failures().stream().map(SourceFailure::reason).toList());
		assertEquals(1, result.entries().size());
	}

	@Test
	void processesMoreFilesThanOneBatch() {
		for (int number = 1; number <= 901; number++) {
			sources.add("pdv1", "n" + number + ".xml", invoice(number, IN_PERIOD));
		}

		assertEquals(901, collect("pdv1").summary().copied());
	}

	@Test
	void readsOnlyTheModelsOfTheCategoryAndStoresInItsFolder() {
		sources.add("nfe", "a.xml", invoice(1, IN_PERIOD));

		service.collect(request(DocumentCategory.NFE_SALES, "nfe"));

		assertEquals(Set.of(Set.of(DocumentModel.NFE)), sources.requestedModels);
		assertEquals(ARCHIVE.resolve("Janeiro_2026").resolve("NFe_Vendas"), archive.stored.get(0).directory());
	}

	@Test
	void savesTheNumbersOfAcceptedInvoicesOnce() {
		sources.add("pdv1", "a.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv2", "copia.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, 5, IN_PERIOD));
		sources.add("pdv2", "fora.xml", invoice(2, 6, AFTER_PERIOD));

		CollectionResult result = collect("pdv1", "pdv2");

		assertEquals(
				Set.of(IssuedNumber.of(invoice(1, 1, IN_PERIOD)), IssuedNumber.of(invoice(2, 5, IN_PERIOD))),
				issuedNumbers.saved);
		assertNull(result.historyFailure());
	}

	@Test
	void reportsAHistoryFailureWithoutLosingTheCollection() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		issuedNumbers.failure = new IOException("banco travado");

		CollectionResult result = collect("pdv1");

		assertEquals("banco travado", result.historyFailure());
		assertEquals(1, result.summary().copied());
		assertEquals(1, result.entries().size());
	}

	@Test
	void savesNoNumbersWhenTheCollectionIsCancelled() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		assertThrows(CancellationException.class,
				() -> service.collect(request("pdv1"), ProgressListener.NONE, () -> true));
		assertEquals(Set.of(), issuedNumbers.saved);
	}

	@Test
	void stopsWhenCancelled() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		assertThrows(CancellationException.class,
				() -> service.collect(request("pdv1"), ProgressListener.NONE, () -> true));
	}

	private CollectionResult collect(String... directories) {
		return service.collect(request(directories));
	}

	private static CollectionRequest request(String... directories) {
		return request(DocumentCategory.NFCE, directories);
	}

	private static CollectionRequest request(DocumentCategory category, String... directories) {
		return new CollectionRequest(List.of(directories), category, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE);
	}

	private static DocumentEntry onlyEntry(CollectionResult result) {
		assertEquals(1, result.entries().size());
		return result.entries().get(0);
	}

	private record Stored(Path directory, String preferredName, String canonicalName, String identity) {
	}

	private static class FakeSources implements SourceFiles, DocumentReader {

		private static final FileTime MODIFIED = FileTime.fromMillis(0);

		private final Map<String, List<SourceFile>> directories = new LinkedHashMap<>();
		private final Map<String, XmlDocument> documents = new HashMap<>();
		private final Set<Set<DocumentModel>> requestedModels = ConcurrentHashMap.newKeySet();

		void add(String directory, String fileName, XmlDocument document) {
			Path path = Path.of(directory, fileName);
			documents.put(path.toString(), document);
			directories.computeIfAbsent(directory, name -> new ArrayList<>()).add(new SourceFile(path, MODIFIED));
		}

		@Override
		public ScanResult scan(Path directory, Path excludedRoot) throws IOException {
			List<SourceFile> files = directories.get(directory.toString());

			if (files == null) {
				throw new NotDirectoryException(directory.toString());
			}

			return new ScanResult(files, List.of());
		}

		@Override
		public byte[] read(SourceFile file) {
			return file.path().toString().getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public XmlDocument read(byte[] content, Set<DocumentModel> acceptedModels) {
			requestedModels.add(acceptedModels);
			return documents.get(new String(content, StandardCharsets.UTF_8));
		}
	}

	private static class FakeIssuedNumbers implements IssuedNumberRepository {

		private final Set<IssuedNumber> saved = new HashSet<>();
		private IOException failure;

		@Override
		public void saveAll(Collection<IssuedNumber> numbers) throws IOException {
			if (failure != null) {
				throw failure;
			}

			saved.addAll(numbers);
		}
	}

	private static class FakeArchive implements DocumentArchive {

		private final List<Stored> stored = new ArrayList<>();
		private String failingName;

		void failOn(String fileName) {
			this.failingName = fileName;
		}

		@Override
		public synchronized Outcome store(byte[] content, FileTime sourceModified, Path directory, String preferredName,
				String canonicalName, String identity) throws IOException {

			if (preferredName.equals(failingName)) {
				throw new IOException("disk full");
			}

			stored.add(new Stored(directory, preferredName, canonicalName, identity));
			return Outcome.COPIED;
		}
	}
}
