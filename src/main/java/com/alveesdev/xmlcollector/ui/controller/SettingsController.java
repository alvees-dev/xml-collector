package com.alveesdev.xmlcollector.ui.controller;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.connection.DirectoryTester;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.ui.model.VoidingText;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class SettingsController {

	private static final DateTimeFormatter VALIDITY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final VoidingBatch voidingBatch;
	private final DirectoryTester directoryTester;

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
	private ChoiceBox<SeparationMode> choiceSeparation;

	@FXML
	private RadioButton radioSingleFolder;

	@FXML
	private RadioButton radioByDay;

	@FXML
	private TextField txtEmailUser;

	@FXML
	private PasswordField txtEmailPassword;

	@FXML
	private TextField txtCertificatePath;

	@FXML
	private PasswordField txtCertificatePassword;

	@FXML
	private ChoiceBox<FiscalEnvironment> choiceEnvironment;

	@FXML
	private Label lblEnvironmentHint;

	private DirectoryListEditor pdvEditor;
	private DirectoryListEditor nfeEditor;

	private double dragOffsetX;
	private double dragOffsetY;

	public SettingsController(SettingsService settingsService, SearchModelCatalog searchModels, VoidingBatch voidingBatch,
			DirectoryTester directoryTester) {
		this.settingsService = settingsService;
		this.searchModels = searchModels;
		this.voidingBatch = voidingBatch;
		this.directoryTester = directoryTester;
	}

	@FXML
	public void initialize() {
		AppSettings settings = settingsService.current();

		pdvEditor = new DirectoryListEditor(pdvListBox, pdvScrollPane, "PDV", 45, 219, directoryTester, this::pdvTarget);
		nfeEditor = new DirectoryListEditor(nfeListBox, nfeScrollPane, "NFe", 45, 219, directoryTester, this::nfeTarget);

		pdvEditor.load(settings.pdvDirectories());
		nfeEditor.load(settings.nfeDirectories());

		loadSearchModels(settings);

		choiceSeparation.setConverter(labelled(SeparationMode::label));
		choiceSeparation.getItems().setAll(SeparationMode.BY_PDV, SeparationMode.ALL_TOGETHER);
		choiceSeparation.setValue(settings.separationMode());

		if (settings.organizeMode() == OrganizeMode.BY_DAY) {
			radioByDay.setSelected(true);
		} else {
			radioSingleFolder.setSelected(true);
		}

		loadEmailAccount(settings.emailAccount());
		loadCertificate(settings.certificate());
	}

	@FXML
	private void onBrowseCertificate() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Selecionar certificado A1");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Certificado A1", "*.pfx", "*.p12"));

		File file = chooser.showOpenDialog(btnCancel.getScene().getWindow());

		if (file != null) {
			txtCertificatePath.setText(file.getPath());
		}
	}

	@FXML
	private void onCheckCertificate() {
		try {
			VoidingGatewayFactory.CertificateInfo certificate = voidingBatch.checkCertificate(readCertificate());

			Dialogs.info("Certificado válido.\n\nCNPJ: " + certificate.cnpj()
					+ "\nVálido até: " + VALIDITY_FORMAT.format(certificate.validUntil()));

		} catch (VoidingException e) {
			Dialogs.warning(e.getMessage());
		}
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
	private void onTestAllPdv() {
		pdvEditor.testAll();
	}

	@FXML
	private void onTestAllNfe() {
		nfeEditor.testAll();
	}

	private DirectoryTarget pdvTarget(String directory) {
		PdvSearchModel model = choicePdvModel.getValue();

		if (model == null) {
			return new DirectoryTarget(directory, List.of(), "");
		}

		List<String> folders = Stream.concat(model.collectionFolders().stream(), model.missingFolders().stream())
				.distinct()
				.toList();

		return new DirectoryTarget(directory, folders, SearchFolderPattern.fixedPrefix(model.voidingFolder()));
	}

	private DirectoryTarget nfeTarget(String directory) {
		NfeSearchModel model = choiceNfeModel.getValue();

		List<String> folders = Stream.concat(model.salesFolders().stream(), model.purchaseFolders().stream())
				.distinct()
				.toList();

		return new DirectoryTarget(directory, folders, null);
	}

	@FXML
	private void onSave() {
		String email = txtEmailUser.getText().trim();

		if (!email.isEmpty() && !email.contains("@")) {
			Dialogs.warning("Informe um e-mail válido.");
			return;
		}

		PdvSearchModel pdvModel = choicePdvModel.getValue();

		if (pdvModel == null && !choicePdvModel.getItems().isEmpty()) {
			Dialogs.warning("Selecione o Modelo de Busca PDV na aba Modelos.");
			return;
		}

		AppSettings settings = new AppSettings(
				pdvEditor.values(),
				nfeEditor.values(),
				choicePdvType.getValue(),
				pdvModel == null ? PdvSearchModel.NONE.id() : pdvModel.id(),
				choiceNfeModel.getValue().id(),
				radioByDay.isSelected() ? OrganizeMode.BY_DAY : OrganizeMode.SINGLE_FOLDER,
				choiceSeparation.getValue(),
				readEmailAccount(email),
				settingsService.current().emailDraft(),
				readCertificate());

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
				.orElse(null));
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

	private void loadCertificate(CertificateSettings certificate) {
		txtCertificatePath.setText(certificate.path());
		txtCertificatePassword.setText(certificate.password());

		choiceEnvironment.setConverter(labelled(VoidingText::environment));
		choiceEnvironment.getItems().setAll(FiscalEnvironment.HOMOLOGATION, FiscalEnvironment.PRODUCTION);
		choiceEnvironment.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> showEnvironmentHint(newValue));
		choiceEnvironment.setValue(certificate.environment());
		showEnvironmentHint(certificate.environment());
	}

	private void showEnvironmentHint(FiscalEnvironment environment) {
		boolean production = environment == FiscalEnvironment.PRODUCTION;

		lblEnvironmentHint.setText(production
				? "Produção inutiliza de verdade, e isso não pode ser desfeito."
				: "Homologação é só um teste, sem efeito fiscal.");
		lblEnvironmentHint.setStyle(production ? "-fx-text-fill: #c62828; -fx-font-weight: bold;" : "");
	}

	private CertificateSettings readCertificate() {
		return new CertificateSettings(
				txtCertificatePath.getText().trim(), txtCertificatePassword.getText(), choiceEnvironment.getValue());
	}

	private void closeWindow() {
		Stage stage = (Stage) btnCancel.getScene().getWindow();
		stage.close();
	}
}
