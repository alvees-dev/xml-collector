package com.alveesdev.xmlcollector.infra.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;

import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;

class SmtpEmailSenderTest {

	@Test
	void theStarttlsUsesTheConnectionThatTrustsTheRootsOfWindows() throws Exception {
		Properties properties = SmtpEmailSender.properties(account(EmailSecurity.STARTTLS, 587));

		assertInstanceOf(SSLSocketFactory.class, properties.get("mail.smtp.ssl.socketFactory"));
		assertEquals("true", properties.get("mail.smtp.starttls.required"));
		assertNull(properties.get("mail.smtp.ssl.enable"));
	}

	@Test
	void theSslUsesTheConnectionThatTrustsTheRootsOfWindows() throws Exception {
		Properties properties = SmtpEmailSender.properties(account(EmailSecurity.SSL, 465));

		assertInstanceOf(SSLSocketFactory.class, properties.get("mail.smtp.ssl.socketFactory"));
		assertEquals("true", properties.get("mail.smtp.ssl.enable"));
	}

	private static EmailAccount account(EmailSecurity security, int port) {
		return new EmailAccount("smtp.example.com", port, security, "sender@example.com", "secret");
	}
}
