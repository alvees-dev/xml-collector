package com.alveesdev.xmlcollector.config;

import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public class AppSettings {

	private static final AppSettings INSTANCE = new AppSettings();

	private List<String> pdvDirectories = new ArrayList<>(List.of(""));

	private List<String> nfeSalesDirectories = new ArrayList<>(List.of(""));

	private List<String> nfePurchaseDirectories = new ArrayList<>(List.of(""));

	private OrganizeMode organizeMode = OrganizeMode.SINGLE_FOLDER;

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

	public List<String> getNfeSalesDirectories() {
		return List.copyOf(nfeSalesDirectories);
	}

	public void setNfeSalesDirectories(List<String> nfeSalesDirectories) {
		this.nfeSalesDirectories = new ArrayList<>(nfeSalesDirectories);
	}

	public List<String> getNfePurchaseDirectories() {
		return List.copyOf(nfePurchaseDirectories);
	}

	public void setNfePurchaseDirectories(List<String> nfePurchaseDirectories) {
		this.nfePurchaseDirectories = new ArrayList<>(nfePurchaseDirectories);
	}

	public OrganizeMode getOrganizeMode() {
		return organizeMode;
	}

	public void setOrganizeMode(OrganizeMode organizeMode) {
		this.organizeMode = organizeMode;
	}
}