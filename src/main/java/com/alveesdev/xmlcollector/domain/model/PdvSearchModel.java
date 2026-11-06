package com.alveesdev.xmlcollector.domain.model;

import java.util.List;

public record PdvSearchModel(
		String id,
		String name,
		PdvType type,
		List<String> collectionFolders,
		List<String> missingFolders,
		String missingFileSuffix,
		String voidingFolder,
		List<String> serverFolders
) {

	public static final PdvSearchModel NONE = new PdvSearchModel(
			"", "Sem modelo (diretório completo)", PdvType.PDV, List.of(), List.of(), "", "", List.of());

	public PdvSearchModel {
		collectionFolders = List.copyOf(collectionFolders);
		missingFolders = List.copyOf(missingFolders);
		serverFolders = List.copyOf(serverFolders);
	}
}
