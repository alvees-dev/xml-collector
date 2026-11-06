package com.alveesdev.xmlcollector.ui.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.dispatch.DispatchMonth;
import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress;
import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.dispatch.ReportOptions;
import com.alveesdev.xmlcollector.application.dispatch.ValuesReportService;
import com.alveesdev.xmlcollector.application.dispatch.ValuesReportText;
import com.alveesdev.xmlcollector.application.port.DirectoryCompressor.CompressedFile;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.DispatchProgressText;
import com.alveesdev.xmlcollector.ui.model.DispatchText;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

public class DispatchTabController {

	private static final String FUNCTION = "Envio";
	private static final String PDF_FUNCTION = "Gerar PDF";
	private static final String COMPRESS_FUNCTION = "Compactar";
	private static final double STEP_LABEL_WIDTH = 140;
	private static final int LISTED_YEARS = 6;

	private final DispatchService service;
	private final ValuesReportService valuesReports;
	private final CollectionService collectionService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final AppPaths paths;

	@FXML
	private RadioButton radioServerSource;

	@FXML
	private TextField txtRecipient;

	@FXML
	private TextField txtSubject;

	@FXML
	private TextArea txtBody;

	@FXML
	private ChoiceBox<Integer> choiceYear;

	@FXML
	private ChoiceBox<String> choiceMonth;

	@FXML
	private CheckBox chkValuesNfce;

	@FXML
	private CheckBox chkValuesNfeSales;

	@FXML
	private CheckBox chkValuesNfePurchases;

	@FXML
	private Button btnSend;

	@FXML
	private Button btnGeneratePdf;

	@FXML
	private Button btnCompress;

	public DispatchTabController(DispatchService service, ValuesReportService valuesReports, CollectionService collectionService, SettingsService settingsService,
			SearchModelCatalog searchModels, AppPaths paths) {
		this.service = service;
		this.valuesReports = valuesReports;
		this.collectionService = collectionService;
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.paths = paths;
	}

	@FXML
	public void initialize() {
		EmailDraft draft = settingsService.current().emailDraft();
		txtRecipient.setText(draft.recipient());
		txtSubject.setText(draft.subject());
		txtBody.setText(draft.body().isBlank() ? EmailDraft.DEFAULT_BODY : draft.body());

		loadMonthChoices();

		txtRecipient.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
		txtSubject.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
		txtBody.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
	}

	@FXML
	private void onSend() {

		Integer year = choiceYear.getSelectionModel().getSelectedItem();
		int monthIndex = choiceMonth.getSelectionModel().getSelectedIndex();

		if (year == null || monthIndex < 0) {
			Dialogs.warning("Selecione o ano e o mês do XML a ser enviado.");
			return;
		}

		String monthLabel = choiceMonth.getSelectionModel().getSelectedItem() + " de " + year;
		YearMonth month = YearMonth.of(year, monthIndex + 1);

		if (txtSubject.getText().isBlank()) {
			Dialogs.warning(DispatchService.SUBJECT_REQUIRED);
			txtSubject.requestFocus();
			return;
		}

		withSourceMonth(btnSend, FUNCTION, month, monthLabel, "enviar", found -> askAboutExistingZipAndSend(found, monthLabel));
	}

	@FXML
	private void onRecipientHelp() {
		Dialogs.info("Destinatário", DispatchText.RECIPIENTS_HELP);
	}

	@FXML
	private void onGeneratePdf() {

		Integer year = choiceYear.getSelectionModel().getSelectedItem();
		int monthIndex = choiceMonth.getSelectionModel().getSelectedIndex();

		if (year == null || monthIndex < 0) {
			Dialogs.warning("Selecione o ano e o mês do relatório.");
			return;
		}

		ReportOptions options = selectedReports();

		if (!options.anySelected()) {
			Dialogs.warning("Marque ao menos um relatório para gerar o PDF.");
			return;
		}

		String monthLabel = choiceMonth.getSelectionModel().getSelectedItem() + " de " + year;
		YearMonth month = YearMonth.of(year, monthIndex + 1);

		withSourceMonth(btnGeneratePdf, PDF_FUNCTION, month, monthLabel, "gerar o relatório", found -> savePdf(found, monthLabel, options));
	}

	@FXML
	private void onCompress() {

		Integer year = choiceYear.getSelectionModel().getSelectedItem();
		int monthIndex = choiceMonth.getSelectionModel().getSelectedIndex();

		if (year == null || monthIndex < 0) {
			Dialogs.warning("Selecione o ano e o mês do XML a ser compactado.");
			return;
		}

		String monthLabel = choiceMonth.getSelectionModel().getSelectedItem() + " de " + year;
		YearMonth month = YearMonth.of(year, monthIndex + 1);

		withSourceMonth(btnCompress, COMPRESS_FUNCTION, month, monthLabel, "compactar", found -> compress(found, monthLabel));
	}

	private void compress(DispatchMonth month, String monthLabel) {

		Optional<CompressedFile> existingZip;

		try {
			existingZip = service.existingZipOf(month);
		} catch (DispatchException e) {
			Dialogs.error(COMPRESS_FUNCTION, e.getMessage());
			return;
		}

		if (existingZip.isPresent() && !Dialogs.confirmReplacingZip(existingZip.get().file())) {
			return;
		}

		ReportOptions reports = selectedReports();
		btnCompress.setDisable(true);
		btnSend.setDisable(true);

		TaskRunner.inBackground("xml-compress", () -> service.compress(month, reports), file -> {
			btnCompress.setDisable(false);
			btnSend.setDisable(false);
			ActivityLog.record(COMPRESS_FUNCTION + ": XML de " + monthLabel + " do " + sourceName(month) + " compactado em " + file.file());
			Dialogs.info(COMPRESS_FUNCTION, DispatchText.compressed(monthLabel, file.file(), file.bytes()));
		}, error -> {
			btnCompress.setDisable(false);
			btnSend.setDisable(false);
			Dialogs.error(COMPRESS_FUNCTION, DispatchText.failure(error));
		});
	}

	private void savePdf(DispatchMonth month, String monthLabel, ReportOptions options) {

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Salvar relatório em PDF");
		chooser.setInitialFileName(ValuesReportText.pdfFileName(month));
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));

		if (Files.isDirectory(month.zipFolder())) {
			chooser.setInitialDirectory(month.zipFolder().toFile());
		} else if (Files.isDirectory(paths.xmlArchiveDir())) {
			chooser.setInitialDirectory(paths.xmlArchiveDir().toFile());
		}

		File chosen = chooser.showSaveDialog(btnGeneratePdf.getScene().getWindow());

		if (chosen == null) {
			return;
		}

		Path file = chosen.toPath();
		btnGeneratePdf.setDisable(true);

		TaskRunner.inBackground("xml-values-pdf", () -> {
			valuesReports.writePdf(month, options, file);
			return file;
		}, saved -> {
			btnGeneratePdf.setDisable(false);
			ActivityLog.record(PDF_FUNCTION + ": relatório de " + monthLabel + " salvo em " + saved);
			Dialogs.info(PDF_FUNCTION, "Relatório de " + monthLabel + " salvo em:\n" + saved);
		}, error -> {
			btnGeneratePdf.setDisable(false);
			Dialogs.error(PDF_FUNCTION, "Não foi possível gerar o PDF: " + FailureText.of(error));
		});
	}

	private ReportOptions selectedReports() {
		return new ReportOptions(chkValuesNfce.isSelected(), chkValuesNfeSales.isSelected(), chkValuesNfePurchases.isSelected());
	}

	// The month of the chosen source; the server is a folder of another system on the network, so finding it does not hold the screen.
	private void withSourceMonth(Button trigger, String function, YearMonth month, String monthLabel, String action, Consumer<DispatchMonth> then) {

		if (!radioServerSource.isSelected()) {
			DispatchMonth collected = DispatchMonth.collected(paths.xmlArchiveDir(), month);

			if (!Files.isDirectory(collected.documents())) {
				Dialogs.warning("Não há pasta coletada para " + monthLabel + ". Faça a coleta desse mês antes de " + action + ".");
				return;
			}

			then.accept(collected);
			return;
		}

		AppSettings settings = settingsService.current();
		List<String> serverFolders = searchModels.pdvModel(settings.pdvModelId()).serverFolders();

		if (settings.serverDirectory().isBlank()) {
			Dialogs.warning("Informe o diretório do Servidor em Configurações > Diretórios.");
			return;
		}

		if (serverFolders.isEmpty()) {
			Dialogs.warning("O Modelo de Busca PDV selecionado não informa as pastas do servidor.");
			return;
		}

		trigger.setDisable(true);

		TaskRunner.inBackground("xml-server-month", () -> collectionService.monthFolderOf(settings.serverDirectory(), serverFolders, month),
				found -> {
					trigger.setDisable(false);

					if (found.isEmpty()) {
						Dialogs.warning("Não há pasta do servidor para " + monthLabel + " em:\n" + settings.serverDirectory());
						return;
					}

					then.accept(DispatchMonth.server(found.get(), paths.xmlArchiveDir(), month));
				},
				error -> {
					trigger.setDisable(false);
					Dialogs.error(function, "Não foi possível abrir o diretório do Servidor: " + FailureText.of(error));
				});
	}

	private void askAboutExistingZipAndSend(DispatchMonth month, String monthLabel) {

		Optional<CompressedFile> existingZip;

		try {
			existingZip = service.existingZipOf(month);
		} catch (DispatchException e) {
			Dialogs.error(FUNCTION, e.getMessage());
			return;
		}

		if (existingZip.isEmpty()) {
			send(month, monthLabel, false, DispatchProgressText.stepLabels());
			return;
		}

		switch (Dialogs.askAboutExistingZip()) {
			case REPLACE -> send(month, monthLabel, false, DispatchProgressText.stepLabels());
			case SEND_EXISTING -> send(month, monthLabel, true,
					DispatchProgressText.stepLabelsOfExistingFile(!DispatchService.fitsInMessage(List.of(existingZip.get()))));
			case CANCEL -> {
			}
		}
	}

	private void send(DispatchMonth month, String monthLabel, boolean reuseExistingZip, Map<Integer, String> steps) {

		updateDraft();
		saveSettings();

		AppSettings settings = settingsService.current();
		EmailDraft draft = settings.emailDraft();

		DispatchRequest request = new DispatchRequest(
				List.of(month), settings.emailAccount(), draft.recipient().trim(), draft.subject(), draft.body(), reuseExistingZip,
				selectedReports());

		ActivityLog.record(DispatchText.started(monthLabel, sourceName(month), request.recipient(), reuseExistingZip, request.reports()));

		TaskRunner.withProgress(btnSend, "xml-dispatch",
				() -> new ProgressWindow(btnSend.getScene().getWindow(), "Enviando arquivo XML de " + monthLabel, steps, STEP_LABEL_WIDTH)
						.withDetail("Destino: " + request.recipient()),
				window -> new DispatchTask(service, request, progress -> {
					recordStep(progress);
					window.update(progress);
				}),
				new TaskRunner.Handlers<>(result -> Dialogs.dispatchCompleted(DispatchText.success(monthLabel, request.recipient(), result)),
						error -> Dialogs.error(FUNCTION, DispatchText.failure(error)),
						() -> Dialogs.warning("Envio cancelado. O e-mail não foi enviado.")));
	}

	private static String sourceName(DispatchMonth month) {
		return month.source() == DispatchMonth.Source.SERVER ? "Diretório Servidor" : "Diretório Coletado";
	}

	private static void recordStep(DispatchProgress progress) {
		if (progress.stage() == DispatchProgress.Stage.RUNNING) {
			ActivityLog.record(FUNCTION + ": " + DispatchProgressText.stepLabels().get(progress.step().ordinal()));
		}
	}

	private void loadMonthChoices() {
		YearMonth currentMonth = YearMonth.now();
		YearMonth previousMonth = currentMonth.minusMonths(1);

		List<Integer> years = new ArrayList<>();

		for (int year = currentMonth.getYear(); year > currentMonth.getYear() - LISTED_YEARS; year--) {
			years.add(year);
		}

		choiceYear.getItems().setAll(years);
		choiceMonth.getItems().setAll(ArchiveLayout.monthNames());

		choiceYear.getSelectionModel().select(years.indexOf(previousMonth.getYear()));
		choiceMonth.getSelectionModel().select(previousMonth.getMonthValue() - 1);
	}

	private void updateDraft() {
		settingsService.updateDraft(new EmailDraft(txtRecipient.getText(), txtSubject.getText(), txtBody.getText()));
	}

	private void saveSettings() {
		try {
			settingsService.save(settingsService.current());
		} catch (IOException e) {
			Dialogs.warning("Não foi possível salvar as configurações: " + e.getMessage());
		}
	}

}
