package com.alveesdev.xmlcollector.application.port;

public class VoidingException extends Exception {

	private static final long serialVersionUID = 1L;

	public VoidingException(String message) {
		super(message);
	}

	public VoidingException(String message, Throwable cause) {
		super(message, cause);
	}
}
