package com.alveesdev.xmlcollector.domain.model;

import java.util.Objects;

public record NfceEntry(NfceStatus status, String fileName, XmlDocument.Nfce nfce) {

	public NfceEntry {
		Objects.requireNonNull(status);
		Objects.requireNonNull(fileName);
	}

	public static NfceEntry of(XmlDocument.Nfce nfce, boolean cancelled, String fileName) {
		return new NfceEntry(cancelled ? NfceStatus.CANCELLED : NfceStatus.AUTHORIZED, fileName, nfce);
	}

	public static NfceEntry error(String fileName) {
		return new NfceEntry(NfceStatus.ERROR, fileName, null);
	}
}