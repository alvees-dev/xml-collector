package com.alveesdev.xmlcollector.infra.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.port.EmailSender;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.infra.security.SystemTrust;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

public class SmtpEmailSender implements EmailSender {

	private static final Logger LOG = Logger.getLogger(SmtpEmailSender.class.getName());

	private static final String CONNECTION_TIMEOUT_MILLIS = "30000";
	private static final String READ_TIMEOUT_MILLIS = "120000";
	private static final String WRITE_TIMEOUT_MILLIS = "900000";

	@Override
	public void send(EmailAccount account, EmailMessage message) throws DispatchException {

		Session session = Session.getInstance(properties(account), new Authenticator() {

			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(account.username(), account.password());
			}
		});

		try {
			MimeMessage mime = new MimeMessage(session);
			mime.setFrom(new InternetAddress(account.username()));
			mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.recipient()));
			mime.setSubject(message.subject(), StandardCharsets.UTF_8.name());
			mime.setSentDate(new Date());

			MimeMultipart content = new MimeMultipart();

			MimeBodyPart text = new MimeBodyPart();
			text.setText(message.body(), StandardCharsets.UTF_8.name());
			content.addBodyPart(text);

			for (Path attachment : message.attachments()) {
				MimeBodyPart part = new MimeBodyPart();
				part.attachFile(attachment.toFile());
				content.addBodyPart(part);
			}

			mime.setContent(content);

			Transport.send(mime);

		} catch (AuthenticationFailedException e) {
			throw new DispatchException(
					"O servidor recusou o usuário ou a senha do e-mail. \nNo Gmail, use uma senha de app.", e);
		} catch (AddressException e) {
			throw new DispatchException("Endereço de e-mail inválido. Confira o remetente e o destinatário.", e);
		} catch (MessagingException | IOException e) {
			LOG.log(Level.WARNING, "Falha ao enviar o e-mail", e);
			throw new DispatchException(failure(e), e);
		}
	}

	// The first message only says what step failed; the reason is in the deepest cause, so it goes on the screen too.
	static String failure(Exception error) {

		String message = "Falha ao enviar o e-mail: " + error.getMessage();

		Throwable root = error;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}

		String reason = root.getMessage();

		if (root == error || reason == null || reason.isBlank() || reason.equals(error.getMessage())) {
			return message;
		}

		return message + "\nCausa: " + reason;
	}

	static Properties properties(EmailAccount account) throws DispatchException {

		Properties properties = new Properties();
		// The same socket factory serves the SSL from the start and the STARTTLS.
		properties.put("mail.smtp.ssl.socketFactory", socketFactory());
		properties.put("mail.smtp.host", account.host());
		properties.put("mail.smtp.port", String.valueOf(account.port()));
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.connectiontimeout", CONNECTION_TIMEOUT_MILLIS);
		properties.put("mail.smtp.timeout", READ_TIMEOUT_MILLIS);
		properties.put("mail.smtp.writetimeout", WRITE_TIMEOUT_MILLIS);
		properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");

		if (account.security() == EmailSecurity.SSL) {
			properties.put("mail.smtp.ssl.enable", "true");
		} else {
			properties.put("mail.smtp.starttls.enable", "true");
			properties.put("mail.smtp.starttls.required", "true");
		}

		return properties;
	}

	// An antivirus that checks the emails, like Avast, answers with its own certificate, which it installed in Windows.
	// Java alone does not know it, so the connection trusts the roots of Windows too.
	private static SSLSocketFactory socketFactory() throws DispatchException {
		try {
			return SystemTrust.context().getSocketFactory();
		} catch (GeneralSecurityException | IOException e) {
			throw new DispatchException("Não foi possível preparar a conexão segura com o servidor de e-mail: " + e.getMessage(), e);
		}
	}
}