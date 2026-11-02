package com.alveesdev.xmlcollector.application.settings;

public final class DefaultEmailAccount {

	private static final String HOST = "smtp.gmail.com";
	private static final int PORT = 587;
	private static final EmailSecurity SECURITY = EmailSecurity.STARTTLS;
	private static final String USERNAME = "";
	private static final String PASSWORD = "";

	private DefaultEmailAccount() {
	}

	public static EmailAccount get() {
		return new EmailAccount(HOST, PORT, SECURITY, USERNAME, PASSWORD);
	}
}