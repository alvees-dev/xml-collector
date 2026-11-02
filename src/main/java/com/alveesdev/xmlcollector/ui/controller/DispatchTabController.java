package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.alveesdev.xmlcollector.application.DispatchException;
import com.alveesdev.xmlcollector.application.DispatchRequest;
import com.alveesdev.xmlcollector.application.DispatchResult;
import com.alveesdev.xmlcollector.application.DispatchServiceFactory;
import com.alveesdev.xmlcollector.application.DispatchService;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.EmailSettingsStore;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class DispatchTabController {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;
	private static final String SENDING_TEXT = "Enviando...";
	private static final int LISTED_YEARS = 6;

	private final TextField recipientField;
	private final TextField subjectField;
	private final TextArea bodyArea;
	private final ChoiceBox<Integer> yearChoice;
	private final ChoiceBox<String> monthChoice;
	private final Button sendButton;
	private final String sendButtonText;
	private final AppPaths paths;
	private final DispatchService service;
	private final EmailSettingsStore store;

	public DispatchTabController(TextField recipientField, TextField subjectField, TextArea bodyArea,
			ChoiceBox<Integer> yearChoice, ChoiceBox<String> monthChoice, Button sendButton, AppPaths paths) {
		this.recipientField = recipientField;
		this.subjectField = subjectField;
		this.bodyArea = bodyArea;
		this.yearChoice = yearChoice;
		this.monthChoice = monthChoice;
		this.sendButton = sendButton;
		this.sendButtonText = sendButton.getText();
		this.paths = paths;
		this.service = DispatchServiceFactory.standard(paths);
		this.store = new EmailSettingsStore(paths.emailSettingsFile());
	}

	public void load() {
		AppSettings settings = AppSettings.getInstance();

		try {
			store.load(settings);
		} catch (IOException e) {
			Platform.runLater(() -> Dialogs.warning("Não foi possível ler as configurações de e-mail: " + e.getMessage()));
		}

		EmailDraft draft = settings.getEmailDraft();
		recipientField.setText(draft.recipient());
		subjectField.setText(draft.subject());
		bodyArea.setText(draft.body());

		loadMonthChoices();

		recipientField.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
		subjectField.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
		bodyArea.textProperty().addListener((observable, oldValue, newValue) -> updateDraft());
	}

	public void send() {

		Integer year = yearChoice.getSelectionModel().getSelectedItem();
		int monthIndex = monthChoice.getSelectionModel().getSelectedIndex();

		if (year == null || monthIndex < 0) {
			Dialogs.warning("Selecione o ano e o mês do XML a ser enviado.");
			return;
		}

		String monthLabel = monthChoice.getSelectionModel().getSelectedItem() + " de " + year;
		Path monthDirectory = ArchiveLayout.monthDirectory(paths.xmlArchiveDir(), YearMonth.of(year, monthIndex + 1).atDay(1));

		if (!Files.isDirectory(monthDirectory)) {
			Dialogs.warning("Não há pasta coletada para " + monthLabel + ". Faça a coleta desse mês antes de enviar.");
			return;
		}

		updateDraft();
		saveSettings();

		AppSettings settings = AppSettings.getInstance();
		EmailDraft draft = settings.getEmailDraft();

		DispatchRequest request = new DispatchRequest(
				List.of(monthDirectory), settings.getEmailAccount(), draft.recipient().trim(), draft.subject(), draft.body());

		DispatchTask task = new DispatchTask(service, request);

		sendButton.setDisable(true);
		sendButton.setText(SENDING_TEXT);

		task.setOnSucceeded(event -> {
			restoreButton();
			Dialogs.info(describeSuccess(monthLabel, request.recipient(), task.getValue()));
		});

		task.setOnFailed(event -> {
			restoreButton();
			Dialogs.error(describeFailure(task.getException()));
		});

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

		yearChoice.getItems().setAll(years);
		monthChoice.getItems().setAll(ArchiveLayout.monthNames());

		yearChoice.getSelectionModel().select(years.indexOf(previousMonth.getYear()));
		monthChoice.getSelectionModel().select(previousMonth.getMonthValue() - 1);
	}

	private void updateDraft() {
		AppSettings.getInstance().setEmailDraft(
				new EmailDraft(recipientField.getText(), subjectField.getText(), bodyArea.getText()));
	}

	private void saveSettings() {
		try {
			store.save(AppSettings.getInstance());
		} catch (IOException e) {
			Dialogs.warning("Não foi possível salvar as configurações de e-mail: " + e.getMessage());
		}
	}

	private void restoreButton() {
		sendButton.setDisable(false);
		sendButton.setText(sendButtonText);
	}

	private static String describeSuccess(String monthLabel, String recipient, DispatchResult result) {

		String size = String.format(BRAZIL, "%.1f MB", result.totalZipBytes() / BYTES_PER_MEGABYTE);

		if (result.method() == DispatchResult.Method.ATTACHED) {
			return "E-mail enviado para " + recipient + " com o XML de " + monthLabel
					+ " em anexo (" + size + ").";
		}

		return "O XML de " + monthLabel + " (" + size + ") passou do limite de 25 MB e foi salvo no Google Drive."
				+ "\nO link da pasta foi enviado para " + recipient + "."
				+ "\n\nLink: " + result.driveLink();
	}

	private static String describeFailure(Throwable error) {
		if (error instanceof DispatchException) {
			return error.getMessage();
		}

		return "Erro inesperado ao enviar o e-mail: " + error.getMessage();
	}
}