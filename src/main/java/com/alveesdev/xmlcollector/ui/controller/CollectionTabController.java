package com.alveesdev.xmlcollector.ui.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

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
import com.alveesdev.xmlcollector.ui.model.DocumentRow;
import com.alveesdev.xmlcollector.ui.model.DocumentRowMapper;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class CollectionTabController {

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;

	private CollectedDocuments collected = new CollectedDocuments();

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
	private TextField txtNfceTotal;

	@FXML
	private TableView<DocumentRow> documentsTable;

	@FXML
	private TableColumn<DocumentRow, String> numberColumn;

	@FXML
	private TableColumn<DocumentRow, String> seriesColumn;

	@FXML
	private TableColumn<DocumentRow, String> totalValueColumn;

	@FXML
	private TableColumn<DocumentRow, String> emissionDateColumn;

	@FXML
	private TableColumn<DocumentRow, String> accessKeyColumn;

	@FXML
	private TableColumn<DocumentRow, String> statusColumn;

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
		numberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));
		seriesColumn.setCellValueFactory(new PropertyValueFactory<>("series"));
		totalValueColumn.setCellValueFactory(new PropertyValueFactory<>("totalValue"));
		emissionDateColumn.setCellValueFactory(new PropertyValueFactory<>("emissionDate"));
		accessKeyColumn.setCellValueFactory(new PropertyValueFactory<>("accessKey"));
		statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

		radioPdvDirectories.setSelected(true);

		txtDirectory.disableProperty().bind(radioPdvDirectories.selectedProperty());

		YearMonth previousMonth = YearMonth.now().minusMonths(1);
		dpFrom.setValue(previousMonth.atDay(1));
		dpTo.setValue(previousMonth.atEndOfMonth());
	}

	@FXML
	private void onCollect() {

		SourceMode sourceMode = selectedSourceMode();
		Optional<CollectionRequest> request = buildRequest(sourceMode);

		if (request.isEmpty()) {
			return;
		}

		CollectionRunner.run(btnCollect, collectionService, request.get(), sourceMode,
				result -> showResult(request.get(), result, sourceMode));
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

		return Optional.of(new CollectionRequest(
				directories, DocumentCategory.NFCE, new DateRange(from, to), settings.organizeMode(), paths.xmlArchiveDir(),
				folderPatterns, CollectionTarget.ALL));
	}

	private void showResult(CollectionRequest request, CollectionResult result, SourceMode sourceMode) {

		collected = CollectedDocuments.from(result);
		showCollected();

		onCollected.accept(request, result);

		Dialogs.collectionSummary(result, paths.xmlArchiveDir(), sourceMode);
	}

	private void showCollected() {

		List<DocumentRow> rows = collected.entries().stream()
				.map(DocumentRowMapper::toRow)
				.toList();

		documentsTable.setItems(FXCollections.observableArrayList(rows));
		txtNfceTotal.setText(DocumentRowMapper.currency(collected.authorizedTotal()));
	}
}
