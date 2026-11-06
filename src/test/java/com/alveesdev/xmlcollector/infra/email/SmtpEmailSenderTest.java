package com.alveesdev.xmlcollector.infra.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

import jakarta.mail.MessagingException;

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

	@Test
	void theFailureShowsTheDeepestCause() {
		Exception error = new MessagingException("Could not convert socket to TLS",
				new SSLHandshakeException("PKIX path building failed"));

		assertEquals("Falha ao enviar o e-mail: Could not convert socket to TLS\nCausa: PKIX path building failed",
				SmtpEmailSender.failure(error));
	}

	@Test
	void theFailureWithoutCauseShowsOnlyTheMessage() {
		assertEquals("Falha ao enviar o e-mail: Connection refused",
				SmtpEmailSender.failure(new MessagingException("Connection refused")));
	}

	private static EmailAccount account(EmailSecurity security, int port) {
		return new EmailAccount("smtp.example.com", port, security, "sender@example.com", "secret");
	}
}
