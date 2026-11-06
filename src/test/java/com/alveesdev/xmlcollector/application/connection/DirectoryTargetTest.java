package com.alveesdev.xmlcollector.application.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;

class DirectoryTargetTest {

	@Test
	void aPdvNeedsTheFoldersOfItsModelOnceAndWritesInItsVoidingFolder() {
		PdvSearchModel model = new PdvSearchModel("m", "Modelo", PdvType.PDV, List.of("xml/{AAAA}", "?outros"), List.of("busca", "xml/{AAAA}"), "",
				"xml/inutilizadas/{AAAA}", List.of());

		DirectoryTarget target = DirectoryTarget.ofPdv("\\\\pdv1", model);

		assertEquals("\\\\pdv1", target.directory());
		assertEquals(List.of("xml/{AAAA}", "?outros", "busca"), target.folderPatterns());
		assertEquals("xml/inutilizadas", target.writableFolder());
	}
}
