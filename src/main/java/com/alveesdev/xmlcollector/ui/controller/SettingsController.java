package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class SettingsController {

	private final SettingsService settingsService;

	@FXML
	private Button btnCancel;

	@FXML
	private ModelsTabController modelsTabController;

	@FXML
	private DirectoryListPaneController pdvDirectoriesController;

	@FXML
	private DirectoryListPaneController nfeDirectoriesController;

	@FXML
	private OrganizeTabController organizeTabController;

	@FXML
	private EmailTabController emailTabController;

	@FXML
	private CertificateTabController certificateTabController;

	public SettingsController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@FXML
	public void initialize() {
		AppSettings settings = settingsService.current();

		pdvDirectoriesController.load("Diretório PDV", "PDV", modelsTabController::pdvTarget, settings.pdvDirectories());
		nfeDirectoriesController.load("Diretório NFe", "NFe", modelsTabController::nfeTarget, settings.nfeDirectories());

		modelsTabController.load(settings);
		organizeTabController.load(settings);
		emailTabController.load(settings.emailAccount());
		certificateTabController.load(settings.certificate());
	}

	@FXML
	private void onSave() {

		if (emailTabController.emailIsInvalid()) {
			Dialogs.warning("Informe um e-mail válido.");
			return;
		}

		if (modelsTabController.pdvModelIsMissing()) {
			Dialogs.warning("Selecione o Modelo de Busca PDV na aba Modelos.");
			return;
		}

		AppSettings current = settingsService.current();

		AppSettings settings = new AppSettings(
				pdvDirectoriesController.directories(),
				nfeDirectoriesController.directories(),
				modelsTabController.pdvType(),
				modelsTabController.pdvModelId(),
				modelsTabController.nfeModelId(),
				organizeTabController.organizeMode(),
				organizeTabController.separationMode(),
				emailTabController.accountBasedOn(current.emailAccount()),
				current.emailDraft(),
				certificateTabController.certificate());

		try {
			settingsService.save(settings);
		} catch (IOException e) {
			Dialogs.warning("Não foi possível salvar as configurações: " + e.getMessage());
		}

		closeWindow();
	}

	@FXML
	private void onCancel() {
		closeWindow();
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}
