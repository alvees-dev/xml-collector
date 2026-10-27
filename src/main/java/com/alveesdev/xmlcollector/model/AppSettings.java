package com.alveesdev.xmlcollector.model;

import java.util.ArrayList;
import java.util.List;

public class AppSettings {

	private static final AppSettings INSTANCE = new AppSettings();

	// One empty PDV by default, matching the single field shown on the settings screen
	private List<String> pdvDirectories = new ArrayList<>(List.of(""));

	private AppSettings() {
	}

	public static AppSettings getInstance() {
		return INSTANCE;
	}

	public List<String> getPdvDirectories() {
		return List.copyOf(pdvDirectories);
	}

	public void setPdvDirectories(List<String> pdvDirectories) {
		this.pdvDirectories = new ArrayList<>(pdvDirectories);
	}

	public int getPdvCount() {
		return pdvDirectories.size();
	}
}