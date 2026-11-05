package com.alveesdev.xmlcollector.application.settings;

public record EmailAccount(String host, int port, EmailSecurity security, String username, String password) {

	private static final int MAX_PORT = 65535;

	public boolean isComplete() {
		return host != null && !host.isBlank()
				&& port > 0 && port <= MAX_PORT
				&& security != null
				&& username != null && !username.isBlank()
				&& password != null && !password.isBlank();
	}

	@Override
	public String toString() {
		return "EmailAccount[host=" + host + ", port=" + port + ", security=" + security + ", username=" + username + "]";
	}
}