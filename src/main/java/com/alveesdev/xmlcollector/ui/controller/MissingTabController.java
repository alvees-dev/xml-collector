package com.alveesdev.xmlcollector.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentEntry;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;
import com.alveesdev.xmlcollector.ui.model.MissingTableModel;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

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

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;

	private MissingTableModel model;
	private CollectionRequest lastCollection;
	private SortedMap<Integer, List<NumberRange>> missingBySeries = new TreeMap<>();
	private Consumer<CollectionResult> onFound = result -> {
	};

	@FXML
	private TableView<List<String>> missingTable;

	@FXML
	private Button btnSearchAll;

	@FXML
	private Button btnSearchNumber;

	@FXML
	private TextField txtSearchNumber;

	public MissingTabController(CollectionService collectionService, SettingsService settingsService,
			SearchModelCatalog searchModels, AppPaths paths) {
		this.collectionService = collectionService;
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.paths = paths;
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

		search(btnSearchAll, invoice -> wanted.getOrDefault(invoice.series(), List.of()).stream()
				.anyMatch(range -> range.start() <= invoice.number() && invoice.number() <= range.end()));
	}

	@FXML
	private void onSearchNumber() {

		int number;

		try {
			number = Integer.parseInt(txtSearchNumber.getText().trim());
		} catch (NumberFormatException e) {
			Dialogs.warning("Informe o número da NFCe a ser procurada.");
			return;
		}

		search(btnSearchNumber, invoice -> invoice.number() == number);
	}

	private void search(Node trigger, Predicate<XmlDocument.Invoice> wanted) {

		if (lastCollection == null) {
			Dialogs.warning("Faça a coleta primeiro para definir o período da busca.");
			return;
		}

		AppSettings settings = settingsService.current();
		List<String> folders = searchModels.pdvModel(settings.pdvModelId()).missingFolders();

		if (folders.isEmpty()) {
			Dialogs.warning("O modelo de busca selecionado não tem pasta para procurar notas faltantes.");
			return;
		}

		CollectionRequest request = new CollectionRequest(
				settings.pdvDirectories(), DocumentCategory.NFCE, lastCollection.period(), settings.organizeMode(),
				paths.xmlArchiveDir(), folders, new CollectionTarget(index -> true, name -> true, wanted));

		CollectionRunner.run(trigger, collectionService, request, SourceMode.PDV_DIRECTORIES, this::showFound);
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
