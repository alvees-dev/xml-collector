package com.alveesdev.xmlcollector.ui.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.application.collection.CollectedDocuments;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.ui.model.DocumentRow;
import com.alveesdev.xmlcollector.ui.model.DocumentRowMapper;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class CollectionTabController {

	private static final List<String> COLUMN_PROPERTIES = List.of("number", "series", "totalValue", "emissionDate", "accessKey", "status");

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;

	private CollectedDocuments collected = new CollectedDocuments();
	private final Map<DocumentCategory, CollectedDocuments> nfeCollected = new EnumMap<>(DocumentCategory.class);

	private BiConsumer<CollectionRequest, CollectionResult> onCollected = (request, result) -> {
	};

	@FXML
	private DatePicker dpFrom;

	@FXML
	private DatePicker dpTo;

	@FXML
	private RadioButton radioPdvDirectories;

	@FXML
	private TextField txtDirectory;

	@FXML
	private Button btnCollect;

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
			SearchModelCatalog searchModels, AppPaths paths) {
		this.collectionService = collectionService;
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.paths = paths;
	}

	public void setOnCollected(BiConsumer<CollectionRequest, CollectionResult> onCollected) {
		this.onCollected = onCollected;
	}

	public void addFound(CollectionResult result) {
		collected.addFound(result);
		showCollected();
	}

	@FXML
	public void initialize() {
		bindColumns(nfceTable);
		bindColumns(nfeTable);

		radioPdvDirectories.setSelected(true);

		txtDirectory.disableProperty().bind(radioPdvDirectories.selectedProperty());

		YearMonth previousMonth = YearMonth.now().minusMonths(1);
		dpFrom.setValue(previousMonth.atDay(1));
		dpTo.setValue(previousMonth.atEndOfMonth());
	}

	private static void bindColumns(TableView<DocumentRow> table) {
		for (int index = 0; index < COLUMN_PROPERTIES.size(); index++) {
			@SuppressWarnings("unchecked")
			TableColumn<DocumentRow, String> column = (TableColumn<DocumentRow, String>) table.getColumns().get(index);

			column.setCellValueFactory(new PropertyValueFactory<>(COLUMN_PROPERTIES.get(index)));
		}
	}

	@FXML
	private void onCollect() {

		SourceMode sourceMode = selectedSourceMode();
		Optional<CollectionRequest> request = buildRequest(sourceMode);

		if (request.isEmpty()) {
			return;
		}

		Optional<List<CollectionRequest>> nfeRequests = buildNfeRequests();

		if (nfeRequests.isEmpty()) {
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
			showCollected();

			Dialogs.collectionSummary(result, paths.xmlArchiveDir(), SourceMode.NFE_DIRECTORIES, "Coleta de " + label + " concluída");

			collectNfe(requests.subList(1, requests.size()));
		});
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
		return radioPdvDirectories.isSelected() ? SourceMode.PDV_DIRECTORIES : SourceMode.SINGLE_DIRECTORY;
	}

	private Optional<CollectionRequest> buildRequest(SourceMode sourceMode) {

		LocalDate from = dpFrom.getValue();
		LocalDate to = dpTo.getValue();

		if (from == null || to == null || from.isAfter(to)) {
			Dialogs.warning("Informe um período válido (De / Até).");
			return Optional.empty();
		}

		if (sourceMode == SourceMode.SINGLE_DIRECTORY && txtDirectory.getText().isBlank()) {
			Dialogs.warning("Informe o diretório a ser verificado.");
			return Optional.empty();
		}

		AppSettings settings = settingsService.current();

		boolean usesModel = sourceMode == SourceMode.PDV_DIRECTORIES;

		List<String> directories = usesModel
				? settings.pdvDirectories()
				: List.of(txtDirectory.getText().trim());

		List<String> folderPatterns = usesModel
				? searchModels.pdvModel(settings.pdvModelId()).collectionFolders()
				: List.of();

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
