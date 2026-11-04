package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingPlanner;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;
import com.alveesdev.xmlcollector.ui.model.MissingTableModel;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class MissingTabController {

	private static final double COLUMN_WIDTH = 110;
	private static final int MAX_LISTED_PROBLEMS = 15;

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;
	private final VoidingBatch voidingBatch;
	private final VoidingPlanner voidingPlanner;

	private MissingTableModel model;
	private CollectionRequest lastCollection;
	private SortedMap<Integer, List<NumberRange>> missingBySeries = new TreeMap<>();
	private Map<Integer, Set<Integer>> sourcesBySeries = Map.of();
	private Map<Integer, Set<SeriesOwner>> ownersBySeries = Map.of();
	private Map<Integer, Integer> firstNumberBySeries = Map.of();
	private List<String> collectionProblems = List.of();
	private List<String> searchProblems;
	private Consumer<CollectionResult> onFound = result -> {
	};

	@FXML
	private TableView<List<String>> missingTable;

	@FXML
	private Button btnSearchAll;

	@FXML
	private Button btnSearchNumber;

	@FXML
	private TextField txtSearchSeries;

	@FXML
	private TextField txtSearchNumber;

	@FXML
	private Button btnVoidAll;

	@FXML
	private Button btnVoidNumber;

	@FXML
	private TextField txtVoidSeries;

	@FXML
	private TextField txtVoidStart;

	@FXML
	private TextField txtVoidEnd;

	public MissingTabController(CollectionService collectionService, SettingsService settingsService,
			SearchModelCatalog searchModels, AppPaths paths, VoidingBatch voidingBatch, VoidingPlanner voidingPlanner) {
		this.collectionService = collectionService;
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.paths = paths;
		this.voidingBatch = voidingBatch;
		this.voidingPlanner = voidingPlanner;
	}

	public void setOnFound(Consumer<CollectionResult> onFound) {
		this.onFound = onFound;
	}

	@FXML
	public void initialize() {
		refreshPdvColumns();
	}

	public void show(CollectionRequest request, CollectionResult result) {
		lastCollection = request;
		missingBySeries = result.missingBySeries();
		sourcesBySeries = result.sourcesBySeries();
		ownersBySeries = result.ownersBySeries();
		firstNumberBySeries = result.firstNumberBySeries();
		collectionProblems = VoidingPlanner.problemsOf(result);
		searchProblems = null;
		show(MissingTableModel.fromResult(result));
	}

	public void refreshPdvColumns() {

		int pdvCount = settingsService.current().pdvCount();

		boolean keepCurrent = model != null
				&& (model.isSeriesBased() || model.getHeaders().size() == pdvCount);

		if (keepCurrent) {
			return;
		}

		show(MissingTableModel.forPdvs(pdvCount));
	}

	@FXML
	private void onSearchAll() {

		Map<Integer, List<NumberRange>> wanted = missingBySeries;

		if (wanted.values().stream().allMatch(List::isEmpty)) {
			Dialogs.info("Não há notas faltantes para procurar. Faça a coleta primeiro.");
			return;
		}

		search(btnSearchAll, wanted, result -> {
			searchProblems = VoidingPlanner.problemsOf(result);
			showFound(result);
		});
	}

	private IntPredicate sourcesOfMissingSeries(Map<Integer, List<NumberRange>> wanted) {

		// The series of each PDV come from the last collection, which only applies if it read these same PDVs.
		if (!lastCollection.directories().equals(settingsService.current().pdvDirectories())) {
			return index -> true;
		}

		Set<Integer> sources = new HashSet<>();

		wanted.forEach((series, ranges) -> {
			if (!ranges.isEmpty()) {
				sources.addAll(sourcesBySeries.getOrDefault(series, Set.of()));
			}
		});

		return sources.isEmpty() ? index -> true : sources::contains;
	}

	@FXML
	private void onSearchNumber() {

		int series;
		int number;

		try {
			series = Integer.parseInt(txtSearchSeries.getText().trim());
			number = Integer.parseInt(txtSearchNumber.getText().trim());
		} catch (NumberFormatException e) {
			Dialogs.warning("Informe a série e o número da NFCe a ser procurada.");
			return;
		}

		search(btnSearchNumber, Map.of(series, List.of(new NumberRange(number, number))), this::showFound);
	}

	@FXML
	private void onVoidAll() {

		if (lastCollection == null) {
			Dialogs.warning("Não é possível Inutilizar Todas, pois nenhuma coleta foi feita.");
			return;
		}

		Map<Integer, List<NumberRange>> wanted = new TreeMap<>();

		missingBySeries.forEach((series, ranges) -> {
			if (!ranges.isEmpty()) {
				wanted.put(series, ranges);
			}
		});

		voidNumbers(btnVoidAll, wanted, true);
	}

	@FXML
	private void onVoidNumber() {

		int series;
		int start;
		int end;

		try {
			series = Integer.parseInt(txtVoidSeries.getText().trim());
			start = Integer.parseInt(txtVoidStart.getText().trim());
			end = txtVoidEnd.getText().isBlank() ? start : Integer.parseInt(txtVoidEnd.getText().trim());
		} catch (NumberFormatException e) {
			Dialogs.warning("Informe a série e o número da NFCe a ser inutilizada.");
			return;
		}

		if (start > end) {
			Dialogs.warning("O número inicial não pode ser maior que o final.");
			return;
		}

		voidNumbers(btnVoidNumber, Map.of(series, List.of(new NumberRange(start, end))), false);
	}

	private void voidNumbers(Node trigger, Map<Integer, List<NumberRange>> wanted, boolean searchRequired) {

		Optional<String> blocked = voidingBlock(searchRequired);

		if (blocked.isPresent()) {
			Dialogs.warning(blocked.get());
			return;
		}

		AppSettings settings = settingsService.current();
		CertificateSettings certificate = settings.certificate();
		List<VoidingRequest> requests;
		Map<Integer, List<Path>> pdvFolders;

		try {
			requests = voidingPlanner.plan(wanted, missingBySeries, ownersBySeries);
			voidingBatch.checkCertificate(certificate, requests);

			// The month of the collection, so collecting that period again reads the file back.
			pdvFolders = VoidingPlanner.pdvFolders(requests, settings.pdvDirectories(), sourcesBySeries,
					searchModels.pdvModel(settings.pdvModelId()).voidingFolder(), YearMonth.from(lastCollection.period().to()));
			voidingBatch.checkPdvFolders(pdvFolders);
		} catch (VoidingException e) {
			Dialogs.warning(e.getMessage());
			return;
		}

		if (searchProblems == null && !Dialogs.confirmVoidingWithoutSearch()) {
			return;
		}

		if (!Dialogs.confirmVoiding(requests, certificate.environment())) {
			return;
		}

		ProgressWindow progressWindow;

		try {
			progressWindow = new ProgressWindow(trigger.getScene().getWindow(), "Inutilização em andamento...");
		} catch (IOException e) {
			Dialogs.error("Erro ao abrir a tela de progresso: " + e.getMessage());
			return;
		}

		// The month of the collection, so the file goes out with the documents of the gaps it closes.
		VoidingTask task = new VoidingTask(voidingBatch, requests, certificate, paths.xmlArchiveDir(),
				lastCollection.period().to(), pdvFolders);
		progressWindow.setCancelAction(task::requestStop);

		trigger.setDisable(true);

		task.setOnSucceeded(event -> {
			progressWindow.close();
			trigger.setDisable(false);
			showVoided(task.getValue(), certificate.environment());
		});

		task.setOnFailed(event -> {
			progressWindow.close();
			trigger.setDisable(false);
			Dialogs.error(task.getException().getMessage());
		});

		progressWindow.show();

		Thread thread = new Thread(task, "xml-voiding");
		thread.setDaemon(true);
		thread.start();
	}

	private Optional<String> voidingBlock(boolean searchRequired) {

		if (lastCollection == null) {
			return Optional.of("Faça a coleta antes de inutilizar.");
		}

		if (!lastCollection.directories().equals(settingsService.current().pdvDirectories())) {
			return Optional.of("A última coleta não leu os PDVs configurados. Colete por Diretório PDV antes de inutilizar.");
		}

		if (!collectionProblems.isEmpty()) {
			return Optional.of(blockedBy("A coleta teve problemas, e uma nota pode existir sem ter sido lida:", collectionProblems));
		}

		if (searchProblems == null) {
			return searchRequired
					? Optional.of("Clique em Procurar Todas antes de inutilizar, para só inutilizar o que realmente não foi encontrado.")
					: Optional.empty();
		}

		if (!searchProblems.isEmpty()) {
			return Optional.of(blockedBy("A busca teve problemas, e uma nota pode existir sem ter sido lida:", searchProblems));
		}

		return Optional.empty();
	}

	private static String blockedBy(String reason, List<String> problems) {
		return reason + "\n\n" + String.join("\n", problems.stream().limit(MAX_LISTED_PROBLEMS).toList())
				+ "\n\nResolva e faça a coleta de novo antes de inutilizar.";
	}

	private void showVoided(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		// A homologation approval has no fiscal effect, so the numbers are still missing.
		if (environment == FiscalEnvironment.PRODUCTION) {
			SortedMap<Integer, List<NumberRange>> remaining = new TreeMap<>(missingBySeries);

			for (VoidingOutcome outcome : outcomes) {
				if (outcome.stage() == VoidingProgress.Stage.APPROVED) {
					int series = outcome.request().series();
					remaining.put(series, MissingNumberFinder.subtract(remaining.getOrDefault(series, List.of()), List.of(outcome.request().range())));
				}
			}

			missingBySeries = remaining;
			show(MissingTableModel.fromMissing(remaining));
		}

		Dialogs.voidingSummary(outcomes, environment);
	}

	private void search(Node trigger, Map<Integer, List<NumberRange>> wanted, Consumer<CollectionResult> onSuccess) {

		if (lastCollection == null) {
			Dialogs.warning("Faça a coleta primeiro para definir o período da busca.");
			return;
		}

		AppSettings settings = settingsService.current();
		PdvSearchModel searchModel = searchModels.pdvModel(settings.pdvModelId());
		List<String> folders = searchModel.missingFolders();

		if (folders.isEmpty()) {
			Dialogs.warning("O modelo de busca selecionado não tem pasta para procurar notas faltantes.");
			return;
		}

		// A gap before the first number can be the last note of the previous month.
		DateRange period = startsBeforeTheCollection(wanted) ? lastCollection.period().fromThePreviousMonth() : lastCollection.period();

		CollectionRequest request = new CollectionRequest(
				settings.pdvDirectories(), DocumentCategory.NFCE, period, settings.organizeMode(),
				paths.xmlArchiveDir(), folders,
				CollectionTarget.missing(wanted, sourcesOfMissingSeries(wanted), searchModel.missingFileSuffix()),
				settings.separationMode());

		CollectionRunner.run(trigger, "Procurando notas faltantes", collectionService, request, SourceMode.PDV_DIRECTORIES, onSuccess);
	}

	private boolean startsBeforeTheCollection(Map<Integer, List<NumberRange>> wanted) {
		return wanted.entrySet().stream().anyMatch(entry -> entry.getValue().stream()
				.anyMatch(range -> firstNumberBySeries.containsKey(entry.getKey()) && range.start() < firstNumberBySeries.get(entry.getKey())));
	}

	private void showFound(CollectionResult result) {

		List<XmlDocument.Invoice> found = result.entries().stream()
				.map(DocumentEntry::invoice)
				.filter(invoice -> invoice != null)
				.toList();

		SortedMap<Integer, List<NumberRange>> remaining = new TreeMap<>();

		missingBySeries.forEach((series, ranges) -> {
			List<NumberRange> foundInSeries = new ArrayList<>();

			for (XmlDocument.Invoice invoice : found) {
				if (invoice.series() == series) {
					foundInSeries.add(new NumberRange(invoice.number(), invoice.number()));
				}
			}

			remaining.put(series, MissingNumberFinder.subtract(ranges, foundInSeries));
		});

		missingBySeries = remaining;

		if (model != null && model.isSeriesBased()) {
			show(MissingTableModel.fromMissing(remaining));
		}

		onFound.accept(result);

		Dialogs.missingSearchSummary(result, found, paths.xmlArchiveDir());
	}

	private void show(MissingTableModel newModel) {

		model = newModel;

		missingTable.getColumns().clear();

		for (int index = 0; index < newModel.getHeaders().size(); index++) {
			final int columnIndex = index;

			TableColumn<List<String>, String> column = new TableColumn<>(newModel.getHeaders().get(index));
			column.setPrefWidth(COLUMN_WIDTH);
			column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(columnIndex)));

			missingTable.getColumns().add(column);
		}

		missingTable.setItems(FXCollections.observableArrayList(newModel.getRows()));
	}
}
