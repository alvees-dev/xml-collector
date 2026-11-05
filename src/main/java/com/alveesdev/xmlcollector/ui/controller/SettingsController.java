package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;
import com.alveesdev.xmlcollector.ui.model.SettingsChanges;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class SettingsController {

	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;

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

	public SettingsController(SettingsService settingsService, SearchModelCatalog searchModels) {
		this.settingsService = settingsService;
		this.searchModels = searchModels;
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
			recordSaved(current, settings);
		} catch (IOException e) {
			Dialogs.warning("Salvar configurações", "Não foi possível salvar as configurações: " + e.getMessage());
		}

		closeWindow();
	}

	private void recordSaved(AppSettings before, AppSettings after) {
		SettingsChanges.between(before, after,
				id -> searchModels.pdvModel(id).name(),
				id -> searchModels.nfeModel(id).name())
				.forEach(ActivityLog::record);

		ActivityLog.record("Salvar configurações: configurações salvas com sucesso");
	}

	@FXML
	private void onCancel() {
		ActivityLog.record("Configurações fechadas sem salvar");
		closeWindow();
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}
