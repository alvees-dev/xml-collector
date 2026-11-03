package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.DirectoryListEditor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class SettingsController {

	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;

	@FXML
	private Label lblTitle;

	@FXML
	private Button btnSave;

	@FXML
	private Button btnCancel;

	@FXML
	private ChoiceBox<PdvType> choicePdvType;

	@FXML
	private ChoiceBox<PdvSearchModel> choicePdvModel;

	@FXML
	private ChoiceBox<NfeSearchModel> choiceNfeModel;

	@FXML
	private ScrollPane pdvScrollPane;

	@FXML
	private VBox pdvListBox;

	@FXML
	private ScrollPane nfeScrollPane;

	@FXML
	private VBox nfeListBox;

	@FXML
	private RadioButton radioSingleFolder;

	@FXML
	private RadioButton radioByDay;

	@FXML
	private TextField txtEmailUser;

	@FXML
	private PasswordField txtEmailPassword;

	private DirectoryListEditor pdvEditor;
	private DirectoryListEditor nfeEditor;

	private double dragOffsetX;
	private double dragOffsetY;

	public SettingsController(SettingsService settingsService, SearchModelCatalog searchModels) {
		this.settingsService = settingsService;
		this.searchModels = searchModels;
	}

	@FXML
	public void initialize() {
		AppSettings settings = settingsService.current();

		pdvEditor = new DirectoryListEditor(pdvListBox, pdvScrollPane, "PDV", 45, 219);
		nfeEditor = new DirectoryListEditor(nfeListBox, nfeScrollPane, "NFe", 45, 219);

		pdvEditor.load(settings.pdvDirectories());
		nfeEditor.load(settings.nfeDirectories());

		loadSearchModels(settings);

		if (settings.organizeMode() == OrganizeMode.BY_DAY) {
			radioByDay.setSelected(true);
		} else {
			radioSingleFolder.setSelected(true);
		}

		loadEmailAccount(settings.emailAccount());
	}

	@FXML
	private void onAddPdv() {
		pdvEditor.addRow("");
	}

	@FXML
	private void onRemovePdv() {
		pdvEditor.removeLastRow();
	}

	@FXML
	private void onAddNfe() {
		nfeEditor.addRow("");
	}

	@FXML
	private void onRemoveNfe() {
		nfeEditor.removeLastRow();
	}

	@FXML
	private void onSave() {
		String email = txtEmailUser.getText().trim();

		if (!email.isEmpty() && !email.contains("@")) {
			Dialogs.warning("Informe um e-mail válido.");
			return;
		}

		AppSettings settings = new AppSettings(
				pdvEditor.values(),
				nfeEditor.values(),
				choicePdvType.getValue(),
				choicePdvModel.getValue().id(),
				choiceNfeModel.getValue().id(),
				radioByDay.isSelected() ? OrganizeMode.BY_DAY : OrganizeMode.SINGLE_FOLDER,
				settingsService.current().separationMode(),
				readEmailAccount(email),
				settingsService.current().emailDraft());

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

	@FXML
	private void onTitlePressed(MouseEvent event) {
		dragOffsetX = event.getSceneX();
		dragOffsetY = event.getSceneY();
	}

	@FXML
	private void onTitleDragged(MouseEvent event) {
		Stage stage = (Stage) lblTitle.getScene().getWindow();
		stage.setX(event.getScreenX() - dragOffsetX);
		stage.setY(event.getScreenY() - dragOffsetY);
	}

	private void loadSearchModels(AppSettings settings) {
		choicePdvType.setConverter(labelled(PdvType::label));
		choicePdvModel.setConverter(labelled(PdvSearchModel::name));
		choiceNfeModel.setConverter(labelled(NfeSearchModel::name));

		choicePdvType.getItems().setAll(PdvType.values());
		choicePdvType.setValue(settings.pdvType());
		showPdvModels(settings.pdvType(), settings.pdvModelId());

		choicePdvType.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldType, newType) -> showPdvModels(newType, PdvSearchModel.NONE.id()));

		List<NfeSearchModel> nfeModels = searchModels.nfeModels();
		choiceNfeModel.getItems().setAll(nfeModels);
		choiceNfeModel.setValue(nfeModels.stream()
				.filter(model -> model.id().equals(settings.nfeModelId()))
				.findFirst()
				.orElse(NfeSearchModel.NONE));
	}

	private void showPdvModels(PdvType type, String selectedId) {
		List<PdvSearchModel> models = searchModels.pdvModels(type);

		choicePdvModel.getItems().setAll(models);
		choicePdvModel.setValue(models.stream()
				.filter(model -> model.id().equals(selectedId))
				.findFirst()
				.orElse(PdvSearchModel.NONE));
	}

	private static <T> StringConverter<T> labelled(Function<T, String> label) {
		return new StringConverter<>() {

			@Override
			public String toString(T item) {
				return item == null ? "" : label.apply(item);
			}

			@Override
			public T fromString(String text) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private void loadEmailAccount(EmailAccount account) {
		txtEmailUser.setText(account.username());
		txtEmailPassword.setText(account.password());
	}

	private EmailAccount readEmailAccount(String email) {
		EmailAccount current = settingsService.current().emailAccount();

		return new EmailAccount(
				current.host(), current.port(), current.security(), email, txtEmailPassword.getText().trim());
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}
