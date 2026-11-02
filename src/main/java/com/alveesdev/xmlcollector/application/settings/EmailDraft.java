package com.alveesdev.xmlcollector.application.settings;

public record EmailDraft(String recipient, String subject, String body) {

	public static EmailDraft empty() {
		return new EmailDraft("", "", "");
	}
}