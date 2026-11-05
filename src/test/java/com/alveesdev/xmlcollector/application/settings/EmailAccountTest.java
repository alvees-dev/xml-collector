package com.alveesdev.xmlcollector.application.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.dispatch.DispatchRequest;

class EmailAccountTest {

	private static final String PASSWORD = "senha-secreta";
	private static final EmailAccount ACCOUNT = new EmailAccount("smtp.exemplo.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", PASSWORD);

	@Test
	void theTextOfTheAccountHasNoPassword() {
		String text = ACCOUNT.toString();

		assertTrue(text.contains("envio@exemplo.com"), text);
		assertFalse(text.contains(PASSWORD), text);
	}

	@Test
	void whatCarriesTheAccountDoesNotShowThePasswordEither() {
		String settings = AppSettings.defaults().withEmailAccount(ACCOUNT).toString();
		String request = new DispatchRequest(List.of(), ACCOUNT, "contador@exemplo.com", "Assunto", "Corpo", false).toString();

		assertFalse(settings.contains(PASSWORD), settings);
		assertFalse(request.contains(PASSWORD), request);
	}
}
