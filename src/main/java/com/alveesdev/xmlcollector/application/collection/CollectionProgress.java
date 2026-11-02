package com.alveesdev.xmlcollector.application.collection;

public record CollectionProgress(int sourceIndex, int processed, int total) {

	public boolean isCounting() {
		return total < 0;
	}
}