package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.application.collection.CollectedDocuments;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTestResult;
import com.alveesdev.xmlcollector.application.connection.DirectoryTester;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.ui.model.CollectionCounts;
import com.alveesdev.xmlcollector.ui.model.DirectoryTestText;
import com.alveesdev.xmlcollector.ui.model.DocumentRow;
import com.alveesdev.xmlcollector.ui.model.DocumentRowMapper;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.SourceLabels;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.model.StartText;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.FileExplorer;
import com.alveesdev.xmlcollector.ui.view.StatusCell;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

public class CollectionTabController {

	private static final String TEST_PATHS = "Testar Caminhos";

	private static final List<String> COLUMN_PROPERTIES = List.of("number", "series", "totalValue", "emissionDate", "accessKey", "status");
	private static final int STATUS_COLUMN = COLUMN_PROPERTIES.indexOf("status");
	private static final String COLLECTION_DONE = "Coleta concluída";
	private static final String READING_DONE = "Leitura concluída";

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final DirectoryTester directoryTester;
	private final AppPaths paths;

	private CollectedDocuments collected = new CollectedDocuments();
	private final Map<DocumentCategory, CollectedDocuments> nfeCollected = new EnumMap<>(DocumentCategory.class);

	private BiConsumer<CollectionRequest, CollectionResult> onCollected = (request, result) -> {
	};

	private Consumer<CollectionCounts> onCounted = counts -> {
	};

	// What the footer counts: the last collection or reading, and the voidings of its NFCe.
	private String countedAction;
	private DateRange countedPeriod;
	private int voidedNumbers;

	@FXML
	private DatePicker dpFrom;

	@FXML
	private DatePicker dpTo;

	@FXML
	private ChoiceBox<SourceMode> choiceSource;

	private String typedDirectory = "";

	@FXML
	private TextField txtDirectory;

	@FXML
	private Button btnCollect;

	@FXML
	private Button btnRead;

	@FXML
	private Button btnTestPaths;

	private Runnable onOpenDirectories = () -> {
	};

	@FXML
	private CheckBox chkNfce;

	@FXML
	private CheckBox chkNfeSales;

	@FXML
	private CheckBox chkNfePurchases;

	@FXML
	private TextField txtNfceTotal;

	@FXML
	private TextField txtNfeSalesTotal;

	@FXML
	private TextField txtNfePurchasesTotal;

	@FXML
	private TableView<DocumentRow> nfceTable;

	@FXML
	private TableView<DocumentRow> nfeTable;

	public CollectionTabController(CollectionService collectionService, SettingsService settingsService,
			SearchModelCatalog searchModels, DirectoryTester directoryTester, AppPaths paths) {
		this.collectionService = collectionService;
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.directoryTester = directoryTester;
		this.paths = paths;
	}

	public void setOnOpenDirectories(Runnable onOpenDirectories) {
		this.onOpenDirectories = onOpenDirectories;
	}

	@FXML
	private void onOpenDirectories() {
		onOpenDirectories.run();
	}

	// The same test as Testar Todos in the settings, for the PDVs saved there, without opening them.
	@FXML
	private void onTestPaths() {

		AppSettings settings = settingsService.current();
		PdvSearchModel model = searchModels.pdvModel(settings.pdvModelId());
		List<String> directories = settings.pdvDirectories();

		btnTestPaths.setDisable(true);
		btnTestPaths.setText(DirectoryTestText.RUNNING);

		TaskRunner.inBackground("pdv-paths-test", () -> {
			Map<String, DirectoryTestResult> results = new LinkedHashMap<>();

			for (int index = 0; index < directories.size(); index++) {
				results.put(SourceLabels.label(index, SourceMode.PDV_DIRECTORIES), directoryTester.test(DirectoryTarget.ofPdv(directories.get(index).trim(), model)));
			}

			return results;
		}, results -> {
			restoreTestPathsButton();

			String text = DirectoryTestText.summary(results);

			if (results.values().stream().allMatch(result -> result.status() == DirectoryTestResult.Status.OK)) {
				Dialogs.info(TEST_PATHS, text);
			} else {
				Dialogs.warning(TEST_PATHS, text);
			}
		}, error -> {
			restoreTestPathsButton();
			Dialogs.error(TEST_PATHS, FailureText.of(error));
		});
	}

	private void restoreTestPathsButton() {
		btnTestPaths.setDisable(false);
		btnTestPaths.setText(TEST_PATHS);
	}

	public void setOnCollected(BiConsumer<CollectionRequest, CollectionResult> onCollected) {
		this.onCollected = onCollected;
	}

	public void setOnCounted(Consumer<CollectionCounts> onCounted) {
		this.onCounted = onCounted;
	}

	public void addFound(CollectionResult result) {
		collected.addFound(result);
		showCollected();
	}

	@FXML
	public void initialize() {
		bindColumns(nfceTable);
		bindColumns(nfeTable);
		addRowMenu(nfceTable);
		addRowMenu(nfeTable);

		choiceSource.setConverter(ChoiceLabels.of(SourceLabels::name));
		choiceSource.getItems().setAll(SourceMode.SERVER_DIRECTORY, SourceMode.PDV_DIRECTORIES, SourceMode.SINGLE_DIRECTORY);
		choiceSource.setValue(SourceMode.PDV_DIRECTORIES);

		txtDirectory.disableProperty().bind(choiceSource.valueProperty().isNotEqualTo(SourceMode.SINGLE_DIRECTORY));
		txtDirectory.textProperty().addListener((observable, oldText, newText) -> {
			if (choiceSource.getValue() == SourceMode.SINGLE_DIRECTORY) {
				typedDirectory = newText;
			}
		});

		choiceSource.valueProperty().addListener((observable, oldSource, newSource) -> showDirectoryOfTheSource());

		YearMonth previousMonth = YearMonth.now().minusMonths(1);
		dpFrom.setValue(previousMonth.atDay(1));
		dpTo.setValue(previousMonth.atEndOfMonth());
	}

	private static void bindColumns(TableView<DocumentRow> table) {
		for (int index = 0; index < COLUMN_PROPERTIES.size(); index++) {
			@SuppressWarnings("unchecked")
			TableColumn<DocumentRow, String> column = (TableColumn<DocumentRow, String>) table.getColumns().get(index);

			column.setCellValueFactory(new PropertyValueFactory<>(COLUMN_PROPERTIES.get(index)));

			if (index == STATUS_COLUMN) {
				column.setCellFactory(view -> new StatusCell());
			}
		}
	}

	private void addRowMenu(TableView<DocumentRow> table) {
		table.setRowFactory(view -> {
			TableRow<DocumentRow> row = new TableRow<>();

			MenuItem copyKey = new MenuItem("Copiar chave da nota");
			copyKey.setOnAction(event -> copyKeyOf(row.getItem().getEntry()));

			MenuItem openFolder = new MenuItem("Abrir diretório da nota");
			openFolder.setOnAction(event -> openFolderOf(row.getItem().getEntry()));

			ContextMenu menu = new ContextMenu(copyKey, openFolder);

			// A file that could not be read has no key and was not stored.
			menu.setOnShowing(event -> {
				boolean unreadable = row.getItem().getEntry().invoice() == null;
				copyKey.setDisable(unreadable);
				openFolder.setDisable(unreadable);
			});

			row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));

			return row;
		});
	}

	private static void copyKeyOf(DocumentEntry entry) {
		Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, entry.invoice().key().value()));
	}

	// Finding the file reads the documents of the whole month.
	private void openFolderOf(DocumentEntry entry) {
		TaskRunner.inBackground("xml-open-folder", () -> showInExplorer(entry),
				found -> {
					if (!found) {
						Dialogs.warning("O arquivo da nota não foi encontrado em:\n" + paths.xmlArchiveDir());
					}
				},
				error -> Dialogs.error("Não foi possível abrir o diretório da nota: " + FailureText.of(error)));
	}

	private boolean showInExplorer(DocumentEntry entry) throws IOException {
		Optional<Path> file = collectionService.storedFileOf(paths.xmlArchiveDir(), entry);

		if (file.isPresent()) {
			FileExplorer.showSelected(file.get());
		}

		return file.isPresent();
	}

	@FXML
	private void onCollect() {

		if (!chkNfce.isSelected() && !chkNfeSales.isSelected() && !chkNfePurchases.isSelected()) {
			Dialogs.warning("Marque o que deseja coletar: NFCe, NFe Vendas ou NFe Compras.");
			return;
		}

		if (!periodIsValid()) {
			Dialogs.warning("Informe um período válido (De / Até).");
			return;
		}

		SourceMode sourceMode = selectedSourceMode();
		Optional<CollectionRequest> request = chkNfce.isSelected() ? buildRequest(sourceMode) : Optional.empty();

		if (chkNfce.isSelected() && request.isEmpty()) {
			return;
		}

		Optional<List<CollectionRequest>> nfeRequests = buildNfeRequests();

		if (nfeRequests.isEmpty()) {
			return;
		}

		if (!Dialogs.confirmStart(StartText.COLLECTION_HEADER,
				StartText.collection(checkedKinds(), request.isPresent() ? sourceMode : null, dpFrom.getValue(), dpTo.getValue()))) {
			return;
		}

		// Only the NFe: the NFCe already collected stay on the screen and in the missing numbers.
		if (request.isEmpty()) {
			collectNfe(nfeRequests.get());
			return;
		}

		CollectionRunner.run(btnCollect, "Coletando XML", collectionService, request.get(), sourceMode, result -> {
			showResult(request.get(), result, sourceMode);
			collectNfe(nfeRequests.get());
		});
	}

	private void collectNfe(List<CollectionRequest> requests) {

		if (requests.isEmpty()) {
			return;
		}

		CollectionRequest request = requests.get(0);
		String label = nfeLabel(request.category());

		CollectionRunner.run(btnCollect, "Coletando " + label, collectionService, request, SourceMode.NFE_DIRECTORIES, result -> {
			nfeCollected.put(request.category(), CollectedDocuments.from(result));
			countedAs(COLLECTION_DONE, request.period());
			showCollected();

			Dialogs.collectionSummary(result, paths.xmlArchiveDir(), SourceMode.NFE_DIRECTORIES, "Coleta de " + label + " concluída");

			collectNfe(requests.subList(1, requests.size()));
		});
	}

	// Like a collection of the chosen source, missing numbers included, but nothing is copied.
	@FXML
	private void onRead() {

		SourceMode sourceMode = selectedSourceMode();
		Optional<CollectionRequest> request = buildRequest(sourceMode);

		if (request.isEmpty() || !Dialogs.confirmStart(StartText.READING_HEADER, StartText.reading(sourceMode, dpFrom.getValue(), dpTo.getValue()))) {
			return;
		}

		CollectionRunner.read(btnRead, "Lendo XML", collectionService, request.get(), sourceMode, result -> showRead(request.get(), result, sourceMode));
	}

	private List<String> checkedKinds() {
		List<String> kinds = new ArrayList<>();

		if (chkNfce.isSelected()) {
			kinds.add("NFCe");
		}

		if (chkNfeSales.isSelected()) {
			kinds.add("NFe Vendas");
		}

		if (chkNfePurchases.isSelected()) {
			kinds.add("NFe Compras");
		}

		return kinds;
	}

	private void showRead(CollectionRequest request, CollectionResult result, SourceMode sourceMode) {

		collected = CollectedDocuments.from(result);
		nfeCollected.clear();
		countedAs(READING_DONE, request.period());
		voidedNumbers = result.voidedNumbers();
		showCollected();

		onCollected.accept(request, result);

		Dialogs.readingSummary(result, sourceMode);
	}

	// Checked before anything runs, so a problem here does not appear only after the NFCe collection.
	private Optional<List<CollectionRequest>> buildNfeRequests() {

		List<DocumentCategory> categories = new ArrayList<>();

		if (chkNfeSales.isSelected()) {
			categories.add(DocumentCategory.NFE_SALES);
		}

		if (chkNfePurchases.isSelected()) {
			categories.add(DocumentCategory.NFE_PURCHASES);
		}

		if (categories.isEmpty()) {
			return Optional.of(List.of());
		}

		AppSettings settings = settingsService.current();
		NfeSearchModel model = searchModels.nfeModel(settings.nfeModelId());

		if (model.equals(NfeSearchModel.NONE)) {
			Dialogs.warning("Selecione o Modelo de Busca NFe em Configurações antes de coletar.");
			return Optional.empty();
		}

		if (settings.nfeDirectories().stream().allMatch(String::isBlank)) {
			Dialogs.warning("Informe os diretórios de NFe em Configurações antes de coletar.");
			return Optional.empty();
		}

		List<CollectionRequest> requests = new ArrayList<>();

		for (DocumentCategory category : categories) {
			// Without folders the collection would read the whole directory and mix sales with purchases.
			if (model.foldersOf(category).isEmpty()) {
				Dialogs.warning("O Modelo de Busca NFe selecionado não tem pasta para " + nfeLabel(category) + ".");
				return Optional.empty();
			}

			requests.add(new CollectionRequest(
					settings.nfeDirectories(), category, new DateRange(dpFrom.getValue(), dpTo.getValue()), settings.organizeMode(),
					paths.xmlArchiveDir(), model.foldersOf(category), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER));
		}

		return Optional.of(requests);
	}

	private static String nfeLabel(DocumentCategory category) {
		return category == DocumentCategory.NFE_PURCHASES ? "NFe Compras" : "NFe Vendas";
	}

	private SourceMode selectedSourceMode() {
		return choiceSource.getValue();
	}

	// The server comes from the settings, so the field only shows it, and what was typed for the single directory comes back.
	private void showDirectoryOfTheSource() {
		txtDirectory.setText(choiceSource.getValue() == SourceMode.SERVER_DIRECTORY ? settingsService.current().serverDirectory() : typedDirectory);
	}

	public void refreshServerDirectory() {
		showDirectoryOfTheSource();
	}

	private boolean periodIsValid() {
		return dpFrom.getValue() != null && dpTo.getValue() != null && !dpFrom.getValue().isAfter(dpTo.getValue());
	}

	private Optional<CollectionRequest> buildRequest(SourceMode sourceMode) {

		LocalDate from = dpFrom.getValue();
		LocalDate to = dpTo.getValue();

		if (!periodIsValid()) {
			Dialogs.warning("Informe um período válido (De / Até).");
			return Optional.empty();
		}

		if (sourceMode == SourceMode.SINGLE_DIRECTORY && txtDirectory.getText().isBlank()) {
			Dialogs.warning("Informe o diretório a ser verificado.");
			return Optional.empty();
		}

		AppSettings settings = settingsService.current();

		if (sourceMode == SourceMode.SERVER_DIRECTORY && settings.serverDirectory().isBlank()) {
			Dialogs.warning("Informe o diretório do Servidor em Configurações > Diretórios.");
			return Optional.empty();
		}

		boolean usesModel = sourceMode == SourceMode.PDV_DIRECTORIES;

		List<String> directories = switch (sourceMode) {
			case PDV_DIRECTORIES -> settings.pdvDirectories();
			case SERVER_DIRECTORY -> List.of(settings.serverDirectory());
			case SINGLE_DIRECTORY, NFE_DIRECTORIES -> List.of(txtDirectory.getText().trim());
		};

		// The server only goes into the folders of the year and month, when the model says how they are named.
		List<String> folderPatterns = switch (sourceMode) {
			case PDV_DIRECTORIES -> searchModels.pdvModel(settings.pdvModelId()).collectionFolders();
			case SERVER_DIRECTORY -> searchModels.pdvModel(settings.pdvModelId()).serverFolders();
			case SINGLE_DIRECTORY, NFE_DIRECTORIES -> List.of();
		};

		if (usesModel && folderPatterns.isEmpty()) {
			Dialogs.warning("Selecione o Modelo de Busca PDV em Configurações antes de coletar.");
			return Optional.empty();
		}

		return Optional.of(new CollectionRequest(
				directories, DocumentCategory.NFCE, new DateRange(from, to), settings.organizeMode(), paths.xmlArchiveDir(),
				folderPatterns, CollectionTarget.ALL, usesModel ? settings.separationMode() : SeparationMode.ALL_TOGETHER));
	}

	private void showResult(CollectionRequest request, CollectionResult result, SourceMode sourceMode) {

		collected = CollectedDocuments.from(result);
		nfeCollected.clear();
		countedAs(COLLECTION_DONE, request.period());
		voidedNumbers = result.voidedNumbers();
		showCollected();

		onCollected.accept(request, result);

		Dialogs.collectionSummary(result, paths.xmlArchiveDir(), sourceMode, "Coleta concluída");
	}

	private void showCollected() {

		nfceTable.setItems(rowsOf(Stream.of(collected)));
		nfeTable.setItems(rowsOf(nfeCollected.values().stream()));
		txtNfceTotal.setText(DocumentRowMapper.currency(collected.authorizedTotal()));
		txtNfeSalesTotal.setText(nfeTotal(DocumentCategory.NFE_SALES));
		txtNfePurchasesTotal.setText(nfeTotal(DocumentCategory.NFE_PURCHASES));
		publishCounts();
	}

	private void countedAs(String action, DateRange period) {
		countedAction = action;
		countedPeriod = period;
	}

	private void publishCounts() {

		if (countedAction == null) {
			return;
		}

		List<DocumentEntry> onScreen = Stream.concat(Stream.of(collected), nfeCollected.values().stream())
				.flatMap(documents -> documents.entries().stream())
				.toList();

		long xml = onScreen.stream().filter(entry -> entry.invoice() != null).count();
		long cancelled = onScreen.stream().filter(entry -> entry.status() == DocumentStatus.CANCELLED).count();
		long errors = onScreen.stream().filter(entry -> entry.status() == DocumentStatus.ERROR).count();

		onCounted.accept(new CollectionCounts(countedAction, xml, cancelled, voidedNumbers, errors, countedPeriod.from(), countedPeriod.to()));
	}

	private static ObservableList<DocumentRow> rowsOf(Stream<CollectedDocuments> documents) {
		return FXCollections.observableArrayList(documents
				.flatMap(ofCategory -> ofCategory.entries().stream())
				.map(DocumentRowMapper::toRow)
				.toList());
	}

	private String nfeTotal(DocumentCategory category) {
		return DocumentRowMapper.currency(nfeCollected.getOrDefault(category, new CollectedDocuments()).authorizedTotal());
	}
}
