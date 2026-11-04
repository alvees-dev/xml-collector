package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;
import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.ui.model.DispatchProgressText;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.ProgressWindow;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class DispatchTabController {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;
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
		txtBody.setText(draft.body());

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

		updateDraft();
		saveSettings();

		AppSettings settings = settingsService.current();
		EmailDraft draft = settings.emailDraft();

		DispatchRequest request = new DispatchRequest(
				List.of(monthDirectory), settings.emailAccount(), draft.recipient().trim(), draft.subject(), draft.body());

		ProgressWindow progressWindow;

		try {
			progressWindow = new ProgressWindow(btnSend.getScene().getWindow(), "Enviando arquivo XML de " + monthLabel,
					DispatchProgressText.stepLabels(), STEP_LABEL_WIDTH);
		} catch (IOException e) {
			Dialogs.error("Erro ao abrir a tela de progresso: " + e.getMessage());
			return;
		}

		DispatchTask task = new DispatchTask(service, request, progressWindow::update);
		progressWindow.setCancelAction(task::cancel);

		btnSend.setDisable(true);

		task.setOnSucceeded(event -> {
			progressWindow.close();
			btnSend.setDisable(false);
			Dialogs.info(describeSuccess(monthLabel, request.recipient(), task.getValue()));
		});

		task.setOnFailed(event -> {
			progressWindow.close();
			btnSend.setDisable(false);
			Dialogs.error(describeFailure(task.getException()));
		});

		task.setOnCancelled(event -> {
			progressWindow.close();
			btnSend.setDisable(false);
			Dialogs.warning("Envio cancelado. O e-mail não foi enviado.");
		});

		progressWindow.show();

		Thread thread = new Thread(task, "xml-dispatch");
		thread.setDaemon(true);
		thread.start();
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

	private static String describeSuccess(String monthLabel, String recipient, DispatchResult result) {

		String size = String.format(BRAZIL, "%.1f MB", result.totalZipBytes() / BYTES_PER_MEGABYTE);

		if (result.method() == DispatchResult.Method.ATTACHED) {
			return "E-mail enviado para " + recipient + " com o XML de " + monthLabel
					+ " em anexo (" + size + ").";
		}

		String success = "O Arquivo XML de " + monthLabel + " (" + size + ") passou do limite de 25 MB e foi salvo no Google Drive."
				+ "\nO link para acessar a pasta foi enviado para " + recipient + "."
				+ "\n\nLink: " + result.driveLink();

		if (result.driveCleanupFailure() == null) {
			return success;
		}

		return success + "\n\nAtenção: o arquivo anterior não foi removido da pasta e continua ocupando espaço. "
				+ "\nMotivo: " + result.driveCleanupFailure();
	}

	private static String describeFailure(Throwable error) {
		if (error instanceof DispatchException) {
			return error.getMessage();
		}

		return "Erro inesperado ao enviar o e-mail: " + error.getMessage();
	}
}
