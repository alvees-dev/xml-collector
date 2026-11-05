package com.alveesdev.xmlcollector.ui.controller;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class EmailTabController {

	@FXML
	private TextField txtEmailUser;

	@FXML
	private PasswordField txtEmailPassword;

	void load(EmailAccount account) {
		txtEmailUser.setText(account.username());
		txtEmailPassword.setText(account.password());
	}

	boolean emailIsInvalid() {
		String email = email();

		return !email.isEmpty() && !email.contains("@");
	}

	EmailAccount accountBasedOn(EmailAccount current) {
		return new EmailAccount(current.host(), current.port(), current.security(), email(), txtEmailPassword.getText().trim());
	}

	private String email() {
		return txtEmailUser.getText().trim();
	}
}
