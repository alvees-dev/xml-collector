package com.alveesdev.xmlcollector.ui.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.application.voiding.MissingNumbersSession;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.application.voiding.VoidingOutcome;
import com.alveesdev.xmlcollector.application.voiding.VoidingPlanner;
import com.alveesdev.xmlcollector.application.voiding.VoidingPreparation;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.MissingTableModel;
import com.alveesdev.xmlcollector.ui.model.SourceMode;
import com.alveesdev.xmlcollector.ui.model.VoidingText;
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
import javafx.stage.FileChooser;

public class MissingTabController {

	private static final String VOIDING = "Inutilização";
	private static final double COLUMN_WIDTH = 110;

	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;
	private final VoidingBatch voidingBatch;
	private final VoidingPreparation voidingPreparation;

	private final MissingNumbersSession session = new MissingNumbersSession();

	private MissingTableModel model;
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
		this.voidingPreparation = new VoidingPreparation(voidingPlanner, voidingBatch);
	}

	public void setOnFound(Consumer<CollectionResult> onFound) {
		this.onFound = onFound;
	}

	@FXML
	public void initialize() {
		refreshPdvColumns();
	}

	public void show(CollectionRequest request, CollectionResult result) {
		session.registerCollection(request, result);
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

		if (session.nothingMissing()) {
			Dialogs.info("Não há notas faltantes para procurar. Faça a coleta primeiro.");
			return;
		}

		search(btnSearchAll, session.missing(), result -> {
			session.registerSearchOfAll(result);
			showFound(result);
		});
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

		if (!session.hasCollection()) {
			Dialogs.warning("Não é possível Inutilizar Todas, pois nenhuma coleta foi feita.");
			return;
		}

		voidNumbers(btnVoidAll, session.missingWithNumbers(), true);
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

		AppSettings settings = settingsService.current();
		Optional<String> blocked = session.voidingBlock(settings.pdvDirectories(), searchRequired);

		if (blocked.isPresent()) {
			Dialogs.warning(blocked.get());
			return;
		}

		ActivityLog.record("Inutilização solicitada: " + VoidingText.ranges(wanted));

		CertificateSettings certificate = settings.certificate();
		String voidingFolder = searchModels.pdvModel(settings.pdvModelId()).voidingFolder();
		List<String> pdvDirectories = settings.pdvDirectories();

		// A network folder that does not answer can hold this for a long time, and nothing has gone out yet.
		TaskRunner.withProgress(trigger, "xml-voiding-checks",
				() -> new ProgressWindow(trigger.getScene().getWindow(), "Conferindo certificado e pastas..."),
				window -> TaskRunner.taskOf(() -> voidingPreparation.prepare(session, wanted, certificate, pdvDirectories, voidingFolder, paths.xmlArchiveDir())),
				new TaskRunner.Handlers<>(plan -> confirmAndSend(trigger, plan, certificate), MissingTabController::showCheckFailure,
						() -> ActivityLog.record(VOIDING + ": cancelada antes do envio")));
	}

	private static void showCheckFailure(Throwable error) {
		if (error instanceof VoidingException) {
			Dialogs.warning(VOIDING, error.getMessage());
		} else {
			Dialogs.error(VOIDING, FailureText.of(error));
		}
	}

	private void confirmAndSend(Node trigger, VoidingPreparation.Plan plan, CertificateSettings certificate) {

		if (!session.searchedAll() && !Dialogs.confirmVoidingWithoutSearch()) {
			ActivityLog.record(VOIDING + ": cancelada antes do envio");
			return;
		}

		if (!Dialogs.confirmVoiding(plan.requests(), certificate.environment())) {
			ActivityLog.record(VOIDING + ": cancelada antes do envio");
			return;
		}

		ActivityLog.record(VOIDING + ": enviando para a Sefaz em " + VoidingText.environment(certificate.environment()));

		// The month of the collection, so the file goes out with the documents of the gaps it closes.
		TaskRunner.withProgress(trigger, "xml-voiding",
				() -> new ProgressWindow(trigger.getScene().getWindow(), "Inutilização em andamento..."),
				window -> new VoidingTask(voidingBatch, plan.requests(), certificate, paths.xmlArchiveDir(), session.lastCollectedDay(), plan.pdvFolders()),
				new TaskRunner.Handlers<>(outcomes -> showVoided(outcomes, certificate.environment()),
						error -> Dialogs.error(VOIDING, FailureText.of(error)),
						() -> ActivityLog.record(Level.WARNING, VOIDING + ": interrompida, confira na Sefaz o que já foi enviado")));
	}

	private void showVoided(List<VoidingOutcome> outcomes, FiscalEnvironment environment) {

		if (session.registerVoided(outcomes, environment)) {
			show(MissingTableModel.fromMissing(session.missing()));
		}

		Dialogs.voidingSummary(outcomes, environment);

		outcomes.stream().filter(outcome -> outcome.unsavedXml() != null).forEach(this::saveUnsavedXml);
	}

	// The Sefaz will not give this document again, so the person must have the chance of keeping it.
	private void saveUnsavedXml(VoidingOutcome outcome) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Salvar o XML da inutilização");
		chooser.setInitialFileName(ArchiveLayout.voidingFileName(outcome.request().id()));

		File file = chooser.showSaveDialog(missingTable.getScene().getWindow());

		if (file == null) {
			Dialogs.warning(VoidingText.unsavedXml(outcome, "nenhum arquivo foi escolhido"));
			return;
		}

		try {
			Files.writeString(file.toPath(), outcome.unsavedXml());
			ActivityLog.record("XML da inutilização salvo em " + file);
		} catch (IOException e) {
			Dialogs.error(VoidingText.unsavedXml(outcome, FailureText.of(e)));
		}
	}

	private void search(Node trigger, Map<Integer, List<NumberRange>> wanted, Consumer<CollectionResult> onSuccess) {

		if (!session.hasCollection()) {
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

		ActivityLog.record("Busca de notas faltantes solicitada: " + VoidingText.ranges(wanted));

		DateRange period = session.searchPeriod(wanted);

		CollectionRequest request = new CollectionRequest(
				settings.pdvDirectories(), DocumentCategory.NFCE, period, settings.organizeMode(),
				paths.xmlArchiveDir(), folders,
				CollectionTarget.missing(wanted, session.sourcesToSearch(wanted, settings.pdvDirectories()), searchModel.missingFileSuffix()),
				settings.separationMode());

		CollectionRunner.run(trigger, "Procurando notas faltantes", collectionService, request, SourceMode.PDV_DIRECTORIES, onSuccess);
	}

	private void showFound(CollectionResult result) {

		List<XmlDocument.Invoice> found = session.registerFound(result);

		if (model != null && model.isSeriesBased()) {
			show(MissingTableModel.fromMissing(session.missing()));
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
