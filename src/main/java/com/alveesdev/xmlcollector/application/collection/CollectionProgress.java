package com.alveesdev.xmlcollector.application.collection;

public record CollectionProgress(int sourceIndex, Stage stage, int processed, int total) {

	public enum Stage {
		COUNTING,
		READING,
		DONE,
		DONE_WITH_WARNING,
		FAILED
	}

	static CollectionProgress counting(int sourceIndex) {
		return new CollectionProgress(sourceIndex, Stage.COUNTING, 0, 0);
	}

	static CollectionProgress reading(int sourceIndex, int processed, int total) {
		return new CollectionProgress(sourceIndex, Stage.READING, processed, total);
	}

	static CollectionProgress done(int sourceIndex, int total) {
		return new CollectionProgress(sourceIndex, Stage.DONE, total, total);
	}

	static CollectionProgress doneWithWarning(int sourceIndex, int total) {
		return new CollectionProgress(sourceIndex, Stage.DONE_WITH_WARNING, total, total);
	}

	static CollectionProgress failed(int sourceIndex) {
		return new CollectionProgress(sourceIndex, Stage.FAILED, 0, 0);
	}
}
