package com.alveesdev.xmlcollector.domain.model;

import java.util.List;

public record NfeSearchModel(
		String id,
		String name,
		List<String> salesFolders,
		List<String> purchaseFolders
) {

	public static final NfeSearchModel NONE = new NfeSearchModel("", "Nenhum", List.of(), List.of());

	public NfeSearchModel {
		salesFolders = List.copyOf(salesFolders);
		purchaseFolders = List.copyOf(purchaseFolders);
	}

	public List<String> foldersOf(DocumentCategory category) {
		return category == DocumentCategory.NFE_PURCHASES ? purchaseFolders : salesFolders;
	}
}
