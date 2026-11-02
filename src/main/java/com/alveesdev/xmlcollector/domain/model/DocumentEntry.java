package com.alveesdev.xmlcollector.domain.model;

import java.util.Objects;

public record DocumentEntry(DocumentStatus status, String fileName, XmlDocument.Invoice invoice) {

	public DocumentEntry {
		Objects.requireNonNull(status);
		Objects.requireNonNull(fileName);
	}

	public static DocumentEntry of(XmlDocument.Invoice invoice, boolean cancelled, String fileName) {
		return new DocumentEntry(cancelled ? DocumentStatus.CANCELLED : DocumentStatus.AUTHORIZED, fileName, invoice);
	}

	public static DocumentEntry error(String fileName) {
		return new DocumentEntry(DocumentStatus.ERROR, fileName, null);
	}
}