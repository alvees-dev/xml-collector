package com.alveesdev.xmlcollector.service;

public record CollectionProgress(
		String sourceLabel,
		int processed,
		int total,
		String currentFile) {

	public boolean isCounting() {
		return total < 0;
	}

	public double fraction() {
		return total <= 0 ? 0 : (double) processed / total;
	}
}