package com.alveesdev.xmlcollector.domain.model;

import java.util.List;

public record NfeSearchModel(
		String id,
		String name,
		List<String> salesFolders,
		List<String> purchaseFolders
) {

	public static final NfeSearchModel NONE = new NfeSearchModel("", "Sem modelo (diretório completo)", List.of(), List.of());

	public NfeSearchModel {
		salesFolders = List.copyOf(salesFolders);
		purchaseFolders = List.copyOf(purchaseFolders);
	}
}
