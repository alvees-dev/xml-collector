package com.alveesdev.xmlcollector.model;

import java.util.ArrayList;
import java.util.List;

public class CopyReport {

	private int copied;
	private int alreadyExisting;
	private int outOfPeriod;
	private int unreadable;
	private final List<String> failures = new ArrayList<>();

	public int getCopied() {
		return copied;
	}

	public int getAlreadyExisting() {
		return alreadyExisting;
	}

	public int getOutOfPeriod() {
		return outOfPeriod;
	}

	public int getUnreadable() {
		return unreadable;
	}

	public List<String> getFailures() {
		return failures;
	}

	public void incrementCopied() {
		copied++;
	}

	public void incrementAlreadyExisting() {
		alreadyExisting++;
	}

	public void incrementOutOfPeriod() {
		outOfPeriod++;
	}

	public void incrementUnreadable() {
		unreadable++;
	}

	public void addFailure(String failure) {
		failures.add(failure);
	}
}