package com.alveesdev.xmlcollector.ui.controller;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.model.VoidingText;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

public class CertificateTabController {

	private static final String CHECK = "Conferir certificado";
	private static final DateTimeFormatter VALIDITY_FORMAT =DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

	private final VoidingBatch voidingBatch;

	@FXML
	private TextField txtCertificatePath;

	@FXML
	private PasswordField txtCertificatePassword;

	@FXML
	private ChoiceBox<FiscalEnvironment> choiceEnvironment;

	@FXML
	private Label lblEnvironmentHint;

	public CertificateTabController(VoidingBatch voidingBatch) {
		this.voidingBatch = voidingBatch;
	}

	void load(CertificateSettings certificate) {
		txtCertificatePath.setText(certificate.path());
		txtCertificatePassword.setText(certificate.password());

		choiceEnvironment.setConverter(ChoiceLabels.of(VoidingText::environment));
		choiceEnvironment.getItems().setAll(FiscalEnvironment.HOMOLOGATION, FiscalEnvironment.PRODUCTION);
		choiceEnvironment.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> showEnvironmentHint(newValue));
		choiceEnvironment.setValue(certificate.environment());
		showEnvironmentHint(certificate.environment());
	}

	CertificateSettings certificate() {
		return new CertificateSettings(
				txtCertificatePath.getText().trim(), txtCertificatePassword.getText(), choiceEnvironment.getValue());
	}

	@FXML
	private void onBrowseCertificate() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Selecionar certificado A1");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Certificado A1", "*.pfx", "*.p12"));

		File file = chooser.showOpenDialog(txtCertificatePath.getScene().getWindow());

		if (file != null) {
			txtCertificatePath.setText(file.getPath());
		}
	}

	@FXML
	private void onCheckCertificate(ActionEvent event) {

		Node trigger = (Node) event.getSource();
		CertificateSettings settings = certificate();

		trigger.setDisable(true);

		// The certificate can be on a network folder, which may take long to answer.
		TaskRunner.inBackground("xml-certificate-check", () -> voidingBatch.checkCertificate(settings),
				certificate -> {
					trigger.setDisable(false);
					Dialogs.info(CHECK, "Certificado válido.\n\nCNPJ: " + certificate.cnpj()
							+ "\nVálido até: " + VALIDITY_FORMAT.format(certificate.validUntil()));
				},
				error -> {
					trigger.setDisable(false);
					showCertificateFailure(error);
				});
	}

	private static void showCertificateFailure(Throwable error) {
		if (error instanceof VoidingException) {
			Dialogs.warning(CHECK, error.getMessage());
		} else {
			Dialogs.error(CHECK, FailureText.of(error));
		}
	}

	private void showEnvironmentHint(FiscalEnvironment environment) {
		boolean production = environment == FiscalEnvironment.PRODUCTION;

		lblEnvironmentHint.setText(production
				? "Produção inutiliza de verdade, e isso não pode ser desfeito."
				: "Homologação é só um teste, sem efeito fiscal.");
		lblEnvironmentHint.getStyleClass().setAll("label");

		if (production) {
			lblEnvironmentHint.getStyleClass().add("text-danger");
		}
	}
}
