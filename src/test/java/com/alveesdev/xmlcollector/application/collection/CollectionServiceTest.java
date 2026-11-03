package com.alveesdev.xmlcollector.application.collection;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
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
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
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
		assertEquals(ARCHIVE.resolve("2026").resolve("Janeiro").resolve("NFCe"), stored.directory());
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
		assertEquals(ARCHIVE.resolve("2026").resolve("Janeiro").resolve("NFe_Vendas"), archive.stored.get(0).directory());
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
	void reportsOneSourceAtATimeAndFinishesItBeforeTheNext() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "b.xml", invoice(2, IN_PERIOD));
		sources.add("pdv2", "c.xml", invoice(3, IN_PERIOD));
		List<CollectionProgress> events = new ArrayList<>();

		service.collect(request("pdv1", "pdv2"), events::add, () -> false);

		assertEquals(List.of(
				CollectionProgress.counting(0),
				CollectionProgress.reading(0, 0, 2),
				CollectionProgress.reading(0, 1, 2),
				CollectionProgress.reading(0, 2, 2),
				CollectionProgress.done(0, 2),
				CollectionProgress.counting(1),
				CollectionProgress.reading(1, 0, 1),
				CollectionProgress.reading(1, 1, 1),
				CollectionProgress.done(1, 1)), events);
	}

	@Test
	void aFailedSourceIsReportedAndTheNextOneStillRuns() {
		sources.add("pdv2", "a.xml", invoice(1, IN_PERIOD));
		List<CollectionProgress> events = new ArrayList<>();

		CollectionResult result = service.collect(request("sem-acesso", "", "pdv2"), events::add, () -> false);

		assertEquals(CollectionProgress.Stage.FAILED, lastStageOf(events, 0));
		assertEquals(CollectionProgress.Stage.FAILED, lastStageOf(events, 1));
		assertEquals(CollectionProgress.Stage.DONE, lastStageOf(events, 2));
		assertEquals(1, result.entries().size());
	}

	@Test
	void doesNotReportASourceOutOfTheTarget() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, IN_PERIOD));
		List<CollectionProgress> events = new ArrayList<>();

		service.collect(new CollectionRequest(
				List.of("pdv1", "pdv2"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE, List.of(),
				new CollectionTarget(index -> index == 1, name -> true, invoice -> true)), events::add, () -> false);

		assertEquals(Set.of(1), events.stream().map(CollectionProgress::sourceIndex).collect(java.util.stream.Collectors.toSet()));
	}

	private static CollectionProgress.Stage lastStageOf(List<CollectionProgress> events, int sourceIndex) {
		return events.stream()
				.filter(event -> event.sourceIndex() == sourceIndex)
				.reduce((first, second) -> second)
				.orElseThrow()
				.stage();
	}

	@Test
	void stopsWhenCancelled() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		assertThrows(CancellationException.class,
				() -> service.collect(request("pdv1"), ProgressListener.NONE, () -> true));
	}

	@Test
	void readsOnlyTheFoldersOfTheModelInsideEachRoot() {
		sources.add(Path.of("pdv1", "enviados").toString(), "a.xml", invoice(1, IN_PERIOD));
		sources.add(Path.of("pdv1", "outros").toString(), "b.xml", invoice(2, IN_PERIOD));

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of("enviados"), CollectionTarget.ALL));

		assertEquals("a.xml", onlyEntry(result).fileName());
	}

	@Test
	void warnsAboutEachFolderNotFoundAndKeepsReadingTheOthers() {
		sources.add(Path.of("pdv1", "enviados").toString(), "a.xml", invoice(1, IN_PERIOD));
		List<CollectionProgress> events = new ArrayList<>();

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of("enviados", "contingencia"), CollectionTarget.ALL), events::add, () -> false);

		assertEquals(1, result.entries().size());
		assertEquals(
				List.of(new SourceFailure(0, SourceFailure.Reason.FOLDER_NOT_FOUND, Path.of("pdv1", "contingencia").toString())),
				result.failures());
		assertEquals(CollectionProgress.Stage.DONE_WITH_WARNING, lastStageOf(events, 0));
	}

	@Test
	void aSourceWithoutAnyFolderOfTheModelFailsWithOneMessage() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		List<CollectionProgress> events = new ArrayList<>();

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of("enviados", "contingencia"), CollectionTarget.ALL), events::add, () -> false);

		assertEquals(
				List.of(new SourceFailure(0, SourceFailure.Reason.NO_MODEL_FOLDER,
						Path.of("pdv1", "enviados") + ", " + Path.of("pdv1", "contingencia"))),
				result.failures());
		assertEquals(CollectionProgress.Stage.FAILED, lastStageOf(events, 0));
	}

	@Test
	void reportsARootWithoutAnyFolderOfTheModel() {
		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of("enviados"), CollectionTarget.ALL));

		assertEquals(SourceFailure.Reason.NO_MODEL_FOLDER, result.failures().get(0).reason());
	}

	@Test
	void reportsAnInaccessibleRootOfAModel() {
		CollectionResult result = service.collect(new CollectionRequest(
				List.of("sem-acesso"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of("enviados"), CollectionTarget.ALL));

		assertEquals(SourceFailure.Reason.INACCESSIBLE, result.failures().get(0).reason());
	}

	@Test
	void keepsOnlyTheInvoicesAcceptedByTheFilter() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "b.xml", invoice(2, IN_PERIOD));

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of(), CollectionTarget.missing(Map.of(1, List.of(new NumberRange(2, 2))), index -> true, "")));

		assertEquals("b.xml", onlyEntry(result).fileName());
		assertEquals(1, archive.stored.size());
	}

	@Test
	void searchesOnlyInThePdvsOfTheTargetAndDoesNotComplainAboutTheOthers() {
		sources.add("pdv1", "a.xml", invoice(1, 10, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, 15, IN_PERIOD));

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1", "", "pdv2"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of(), CollectionTarget.missing(Map.of(2, List.of(new NumberRange(15, 15))), index -> index == 2, "")));

		assertEquals("b.xml", onlyEntry(result).fileName());
		assertEquals(List.of(), result.failures());
		assertEquals(List.of(Path.of("pdv2", "b.xml").toString()), sources.reads);
	}

	@Test
	void doesNotOpenAFileWhenItsNameShowsItIsNotWanted() {
		String wanted = key(1, 10).value() + "-nfe.xml";
		String other = key(1, 11).value() + "-nfe.xml";
		sources.add("pdv1", wanted, invoice(1, 10, IN_PERIOD));
		sources.add("pdv1", other, invoice(1, 11, IN_PERIOD));
		sources.add("pdv1", "sem-chave.xml", invoice(1, 12, IN_PERIOD));

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of(), CollectionTarget.missing(Map.of(1, List.of(new NumberRange(10, 10))), index -> true, "")));

		assertEquals(wanted, onlyEntry(result).fileName());
		assertEquals(Set.of(Path.of("pdv1", wanted).toString(), Path.of("pdv1", "sem-chave.xml").toString()), Set.copyOf(sources.reads));
	}

	@Test
	void opensOnlyTheFilesWithTheSuffixOfTheModel() {
		String wanted = key(1, 10).value() + "-nfe.xml";
		sources.add("pdv1", wanted, invoice(1, 10, IN_PERIOD));
		sources.add("pdv1", key(1, 10).value() + "-procEventoNFe.xml", cancellation(10, IN_PERIOD));
		sources.add("pdv1", "outro.xml", invoice(1, 10, IN_PERIOD));

		CollectionResult result = service.collect(new CollectionRequest(
				List.of("pdv1"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of(), CollectionTarget.missing(Map.of(1, List.of(new NumberRange(10, 10))), index -> true, "-nfe.xml")));

		assertEquals(wanted, onlyEntry(result).fileName());
		assertEquals(List.of(Path.of("pdv1", wanted).toString()), sources.reads);
	}

	@Test
	void knowsWhichPdvsHoldEachSeries() {
		sources.add("pdv1", "a.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, 1, IN_PERIOD));
		sources.add("pdv2", "c.xml", invoice(1, 2, IN_PERIOD));

		assertEquals(Map.of(1, Set.of(0, 1), 2, Set.of(1)), collect("pdv1", "pdv2").sourcesBySeries());
	}

	@Test
	void aVoidedRangeIsNotMissingAndGoesToItsOwnFolder() {
		sources.add("pdv1", "a.xml", invoice(1, 1, IN_PERIOD));
		sources.add("pdv1", "b.xml", invoice(1, 5, IN_PERIOD));
		sources.add("pdv1", "inut.xml", new XmlDocument.Voiding(
				"ID1", 1, new NumberRange(2, 3), IN_PERIOD.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(-3))));

		CollectionResult result = collect("pdv1");

		assertEquals(Map.of(1, List.of(new NumberRange(4, 4))), result.missingBySeries());
		assertEquals(1, archive.stored.stream().filter(stored -> stored.directory().endsWith("Inutilizadas")).count());
	}

	@Test
	void sumsOnlyTheAuthorizedInvoices() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv1", "b.xml", invoice(2, IN_PERIOD));
		sources.add("pdv1", "c.xml", invoice(3, IN_PERIOD));
		sources.add("pdv2", "evento.xml", cancellation(3, IN_PERIOD));
		sources.add("pdv2", "fora.xml", invoice(4, AFTER_PERIOD));
		sources.add("pdv2", "ruim.xml", new XmlDocument.Unreadable("xml invalido"));

		assertEquals(new java.math.BigDecimal("21.00"), CollectedDocuments.from(collect("pdv1", "pdv2")).authorizedTotal());
	}

	@Test
	void theTotalIsZeroWithoutInvoices() {
		sources.add("pdv1", "evento.xml", cancellation(3, IN_PERIOD));

		assertEquals(java.math.BigDecimal.ZERO, CollectedDocuments.from(collect("pdv1")).authorizedTotal());
	}

	@Test
	void storesEachPdvInItsOwnFolderWithoutASubfolder() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));
		sources.add("pdv2", "b.xml", invoice(2, IN_PERIOD));
		sources.add("pdv2", "inut.xml", new XmlDocument.Voiding(
				"ID1", 1, new NumberRange(5, 5), IN_PERIOD.atTime(12, 0).atOffset(java.time.ZoneOffset.ofHours(-3))));

		collectBy(SeparationMode.BY_PDV, OrganizeMode.SINGLE_FOLDER);

		Path january = ARCHIVE.resolve("2026").resolve("Janeiro");

		assertEquals(Map.of(
				"a.xml", january.resolve("PDV1"),
				"b.xml", january.resolve("PDV2"),
				"1-procInutNFe.xml", january.resolve("PDV2")), storedDirectories());
	}

	@Test
	void keepsThePositionOfThePdvWhenAnEarlierOneIsEmpty() {
		sources.add("pdv2", "a.xml", invoice(1, IN_PERIOD));

		service.collect(new CollectionRequest(
				List.of("", "pdv2"), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, ARCHIVE,
				List.of(), CollectionTarget.ALL, SeparationMode.BY_PDV));

		assertEquals(ARCHIVE.resolve("2026").resolve("Janeiro").resolve("PDV2"), archive.stored.get(0).directory());
	}

	@Test
	void aPdvCanStillBeSeparatedByDay() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		collectBy(SeparationMode.BY_PDV, OrganizeMode.BY_DAY);

		assertEquals(
				ARCHIVE.resolve("2026").resolve("Janeiro").resolve("PDV1").resolve("15-01-2026"),
				archive.stored.get(0).directory());
	}

	@Test
	void allTogetherKeepsTheCategoryFolders() {
		sources.add("pdv1", "a.xml", invoice(1, IN_PERIOD));

		collectBy(SeparationMode.ALL_TOGETHER, OrganizeMode.SINGLE_FOLDER);

		assertEquals(ARCHIVE.resolve("2026").resolve("Janeiro").resolve("NFCe"), archive.stored.get(0).directory());
	}

	private void collectBy(SeparationMode separation, OrganizeMode organize) {
		List<String> directories = sources.directories.keySet().stream().toList();

		service.collect(new CollectionRequest(
				directories, DocumentCategory.NFCE, JANUARY, organize, ARCHIVE, List.of(), CollectionTarget.ALL, separation));
	}

	private Map<String, Path> storedDirectories() {
		Map<String, Path> directories = new HashMap<>();
		archive.stored.forEach(stored -> directories.put(stored.preferredName(), stored.directory()));
		return directories;
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
		private final List<String> reads = java.util.Collections.synchronizedList(new ArrayList<>());
		private final Set<Set<DocumentModel>> requestedModels = ConcurrentHashMap.newKeySet();

		void add(String directory, String fileName, XmlDocument document) {
			Path path = Path.of(directory, fileName);
			documents.put(path.toString(), document);
			directories.computeIfAbsent(directory, name -> new ArrayList<>()).add(new SourceFile(path, MODIFIED));
		}

		@Override
		public ResolvedFolders resolveFolders(Path root, List<String> patterns, DateRange period) throws IOException {
			if (!root.toString().equals("pdv1") && !root.toString().equals("pdv2")) {
				throw new NotDirectoryException(root.toString());
			}

			return new ResolvedFolders(
					patterns.stream()
							.map(root::resolve)
							.filter(folder -> directories.containsKey(folder.toString()))
							.toList(),
					List.of(),
					patterns.stream()
							.map(root::resolve)
							.filter(folder -> !directories.containsKey(folder.toString()))
							.map(Path::toString)
							.toList());
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
			reads.add(file.path().toString());
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
		public void initialize() {
		}

		@Override
		public Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) {
			return Set.of();
		}

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
