package com.alveesdev.xmlcollector.infra.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;

import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.port.EmailSender;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;

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
			throw new DispatchException("Falha ao enviar o e-mail: " + e.getMessage(), e);
		}
	}

	private static Properties properties(EmailAccount account) {

		Properties properties = new Properties();
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
}