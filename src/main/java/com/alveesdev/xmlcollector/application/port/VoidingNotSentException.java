package com.alveesdev.xmlcollector.application.port;

// The connection failed before the request went out, so the Sefaz never saw it and the range can be sent again.
public class VoidingNotSentException extends VoidingException {

	private static final long serialVersionUID = 1L;

	public VoidingNotSentException(String message, Throwable cause) {
		super(message, cause);
	}
}
