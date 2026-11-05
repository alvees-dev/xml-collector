package com.alveesdev.xmlcollector.application.port;

public class DispatchException extends Exception {

	private static final long serialVersionUID = 1L;

	public DispatchException(String message) {
		super(message);
	}

	public DispatchException(String message, Throwable cause) {
		super(message, cause);
	}
}