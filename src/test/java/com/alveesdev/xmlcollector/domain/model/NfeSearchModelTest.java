package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class NfeSearchModelTest {

	private final NfeSearchModel model = new NfeSearchModel("id", "Modelo", List.of("vendas"), List.of("compras"));

	@Test
	void givesTheFoldersOfEachCategory() {
		assertEquals(List.of("vendas"), model.foldersOf(DocumentCategory.NFE_SALES));
		assertEquals(List.of("compras"), model.foldersOf(DocumentCategory.NFE_PURCHASES));
	}
}
