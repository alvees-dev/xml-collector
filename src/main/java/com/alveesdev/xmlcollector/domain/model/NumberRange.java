package com.alveesdev.xmlcollector.domain.model;

public record NumberRange(int start, int end) {

	public NumberRange {
		if (start > end) {
			throw new IllegalArgumentException("Range start is after range end");
		}
	}

	public int size() {
		return end - start + 1;
	}
}