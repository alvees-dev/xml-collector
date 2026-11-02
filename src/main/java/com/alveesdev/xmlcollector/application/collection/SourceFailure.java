package com.alveesdev.xmlcollector.application.collection;

public record SourceFailure(int sourceIndex, Reason reason, String path) {

	public enum Reason {
		NOT_INFORMED,
		INACCESSIBLE,
		SUBFOLDER_INACCESSIBLE
	}
}