package com.alveesdev.xmlcollector.ui.controller;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
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

public class SettingsController {

	private static final int NFE_SALES_INDEX = 0;
	private static final int NFE_PURCHASES_INDEX = 1;

	private final SettingsService settingsService;

	@FXML
	private Label lblTitle;

	@FXML
	private Button btnSave;

	@FXML
	private Button btnCancel;

	@FXML
	private ScrollPane pdvScrollPane;

	@FXML
	private VBox pdvListBox;

	@FXML
	private ChoiceBox<String> choiceNfeType;

	@FXML
	private ScrollPane nfeScrollPane;

	@FXML
	private RadioButton radioSingleFolder;

	@FXML
	private RadioButton radioByDay;

	@FXML
	private TextField txtEmailUser;

	@FXML
	private PasswordField txtEmailPassword;

	private DirectoryListEditor pdvEditor;
	private DirectoryListEditor nfeSalesEditor;
	private DirectoryListEditor nfePurchaseEditor;

	private final VBox nfeSalesListBox = new VBox();
	private final VBox nfePurchaseListBox = new VBox();

	private double dragOffsetX;
	private double dragOffsetY;

	public SettingsController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@FXML
	public void initialize() {
		AppSettings settings = settingsService.current();

		pdvEditor = new DirectoryListEditor(pdvListBox, pdvScrollPane, "PDV", 45, 219);
		nfeSalesEditor = new DirectoryListEditor(nfeSalesListBox, nfeScrollPane, "NFe", 45, 219);
		nfePurchaseEditor = new DirectoryListEditor(nfePurchaseListBox, nfeScrollPane, "NFe", 45, 219);

		pdvEditor.load(settings.pdvDirectories());
		nfeSalesEditor.load(settings.nfeSalesDirectories());
		nfePurchaseEditor.load(settings.nfePurchaseDirectories());

		choiceNfeType.getItems().setAll("NFe Vendas", "NFe Compras");
		choiceNfeType.getSelectionModel().selectedIndexProperty()
				.addListener((observable, oldIndex, newIndex) -> showNfeList(newIndex.intValue()));
		choiceNfeType.getSelectionModel().select(NFE_SALES_INDEX);
		showNfeList(NFE_SALES_INDEX);

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
		selectedNfeEditor().addRow("");
	}

	@FXML
	private void onRemoveNfe() {
		selectedNfeEditor().removeLastRow();
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
				nfeSalesEditor.values(),
				nfePurchaseEditor.values(),
				radioByDay.isSelected() ? OrganizeMode.BY_DAY : OrganizeMode.SINGLE_FOLDER,
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

	private void loadEmailAccount(EmailAccount account) {
		txtEmailUser.setText(account.username());
		txtEmailPassword.setText(account.password());
	}

	private EmailAccount readEmailAccount(String email) {
		EmailAccount current = settingsService.current().emailAccount();

		return new EmailAccount(
				current.host(), current.port(), current.security(), email, txtEmailPassword.getText().trim());
	}

	private void showNfeList(int selectedIndex) {
		nfeScrollPane.setContent(selectedIndex == NFE_PURCHASES_INDEX ? nfePurchaseListBox : nfeSalesListBox);
	}

	private DirectoryListEditor selectedNfeEditor() {
		return choiceNfeType.getSelectionModel().getSelectedIndex() == NFE_PURCHASES_INDEX
				? nfePurchaseEditor
				: nfeSalesEditor;
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}