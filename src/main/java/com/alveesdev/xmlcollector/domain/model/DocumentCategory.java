package com.alveesdev.xmlcollector.domain.model;

public enum DocumentCategory {

	NFCE("NFCe"),
	NFE_PURCHASES("NFe_Compras"),
	NFE_SALES("NFe_Vendas"),
	VOIDED("Inutilizadas");

	private final String folderName;

	DocumentCategory(String folderName) {
		this.folderName = folderName;
	}

	public String folderName() {
		return folderName;
	}
}