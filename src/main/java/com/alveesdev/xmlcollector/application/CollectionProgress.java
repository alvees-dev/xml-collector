package com.alveesdev.xmlcollector.application;

public record CollectionProgress(int sourceIndex, int processed, int total) {

	public boolean isCounting() {
		return total < 0;
	}
}