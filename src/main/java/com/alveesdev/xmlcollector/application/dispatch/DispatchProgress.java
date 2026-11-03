package com.alveesdev.xmlcollector.application.dispatch;

public record DispatchProgress(Step step, Stage stage) {

	public enum Step {
		COMPRESSING,
		UPLOADING,
		SENDING
	}

	public enum Stage {
		RUNNING,
		DONE,
		SKIPPED
	}
}
