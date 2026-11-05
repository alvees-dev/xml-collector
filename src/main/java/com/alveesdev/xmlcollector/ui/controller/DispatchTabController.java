package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress;
import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.dispatch.ReportOptions;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.DispatchProgressText;
import com.alveesdev.xmlcollector.ui.model.DispatchText;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class DispatchTabController {

	private static final String FUNCTION = "Envio";
	private static final double STEP_LABEL_WIDTH = 140;
	private static final int LISTED_YEARS = 6;

	private final DispatchService service;
	private final SettingsService settingsService;
	private final AppPaths paths;

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

	public DispatchTabController(DispatchService service, SettingsService settingsService, AppPaths paths) {
		this.service = service;
		this.settingsService = settingsService;
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
		Path monthDirectory = ArchiveLayout.monthDirectory(paths.xmlArchiveDir(), YearMonth.of(year, monthIndex + 1).atDay(1));

		if (!Files.isDirectory(monthDirectory)) {
			Dialogs.warning("Não há pasta coletada para " + monthLabel + ". Faça a coleta desse mês antes de enviar.");
			return;
		}

		if (txtSubject.getText().isBlank()) {
			Dialogs.warning(DispatchService.SUBJECT_REQUIRED);
			txtSubject.requestFocus();
			return;
		}

		Optional<Boolean> reuseExistingZip = askAboutExistingZip(monthDirectory);

		if (reuseExistingZip.isPresent()) {
			send(monthDirectory, monthLabel, reuseExistingZip.get());
		}
	}

	private Optional<Boolean> askAboutExistingZip(Path monthDirectory) {
		try {
			if (service.existingZipOf(monthDirectory).isEmpty()) {
				return Optional.of(false);
			}
		} catch (DispatchException e) {
			Dialogs.error(FUNCTION, e.getMessage());
			return Optional.empty();
		}

		Dialogs.ExistingZipChoice choice = Dialogs.askAboutExistingZip();

		return choice == Dialogs.ExistingZipChoice.CANCEL ? Optional.empty() : Optional.of(choice == Dialogs.ExistingZipChoice.SEND_EXISTING);
	}

	private void send(Path monthDirectory, String monthLabel, boolean reuseExistingZip) {

		updateDraft();
		saveSettings();

		AppSettings settings = settingsService.current();
		EmailDraft draft = settings.emailDraft();

		DispatchRequest request = new DispatchRequest(
				List.of(monthDirectory), settings.emailAccount(), draft.recipient().trim(), draft.subject(), draft.body(), reuseExistingZip,
				new ReportOptions(chkValuesNfce.isSelected(), chkValuesNfeSales.isSelected(), chkValuesNfePurchases.isSelected()));

		ActivityLog.record(DispatchText.started(monthLabel, request.recipient(), reuseExistingZip, request.reports()));

		TaskRunner.withProgress(btnSend, "xml-dispatch",
				() -> new ProgressWindow(btnSend.getScene().getWindow(), "Enviando arquivo XML de " + monthLabel, DispatchProgressText.stepLabels(), STEP_LABEL_WIDTH),
				window -> new DispatchTask(service, request, progress -> {
					recordStep(progress);
					window.update(progress);
				}),
				new TaskRunner.Handlers<>(result -> Dialogs.info(FUNCTION, DispatchText.success(monthLabel, request.recipient(), result)),
						error -> Dialogs.error(FUNCTION, DispatchText.failure(error)),
						() -> Dialogs.warning("Envio cancelado. O e-mail não foi enviado.")));
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
