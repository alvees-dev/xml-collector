package com.alveesdev.xmlcollector.domain.model;

import java.util.Set;

public enum DocumentCategory {

	// A name with "/" is a folder inside another, as both kinds of NFe live in the folder NFe.

	NFCE("NFCe", DocumentModel.NFCE),
	NFE_PURCHASES("NFe/Compras", DocumentModel.NFE),
	NFE_SALES("NFe/Vendas", DocumentModel.NFE),
	VOIDED("Inutilizadas");

	private final String folderName;
	private final Set<DocumentModel> models;

	DocumentCategory(String folderName, DocumentModel... models) {
		this.folderName = folderName;
		this.models = Set.of(models);
	}

	public String folderName() {
		return folderName;
	}

	public Set<DocumentModel> models() {
		return models;
	}
}
