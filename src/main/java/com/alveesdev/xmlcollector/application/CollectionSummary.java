package com.alveesdev.xmlcollector.application;

import java.util.List;

public record CollectionSummary(
		int copied,
		int renamed,
		int replaced,
		int alreadyExisting,
		int duplicates,
		int outOfPeriod,
		int ignored,
		int errors,
		List<CopyFailure> copyFailures
) {

	public record CopyFailure(String fileName, String reason) {
	}
}