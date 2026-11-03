package com.alveesdev.xmlcollector.domain.model;

public record VoidingReceipt(String status, String reason, String protocol, String receivedAt, String xml) {

	private static final String APPROVED_STATUS = "102";

	public boolean approved() {
		return APPROVED_STATUS.equals(status);
	}
}
