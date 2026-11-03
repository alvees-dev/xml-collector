package com.alveesdev.xmlcollector.application.voiding;

public record VoidingProgress(int index, Stage stage) {

	public enum Stage {
		SENDING,
		APPROVED,
		REJECTED,
		FAILED,
		NOT_SENT
	}
}
