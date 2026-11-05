package com.alveesdev.xmlcollector.application.settings;

public record EmailDraft(String recipient, String subject, String body) {

	public static final String DEFAULT_BODY = "Olá, prezados.\n\nSegue o arquivo XML.\nAtenciosamente,";

	public static EmailDraft empty() {
		return new EmailDraft("", "", "");
	}
}
