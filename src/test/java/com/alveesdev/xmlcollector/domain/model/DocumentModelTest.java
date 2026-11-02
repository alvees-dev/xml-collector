package com.alveesdev.xmlcollector.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DocumentModelTest {

	private static final String KEY_START = "35260112345678000190";
	private static final String KEY_END = "0010000000011000000001";

	@Test
	void readsTheModelFromTheAccessKey() {
		assertEquals(Optional.of(DocumentModel.NFCE), new AccessKey(KEY_START + "65" + KEY_END).model());
		assertEquals(Optional.of(DocumentModel.NFE), new AccessKey(KEY_START + "55" + KEY_END).model());
	}

	@Test
	void anUnknownModelIsEmptyButKeepsItsCode() {
		AccessKey key = new AccessKey(KEY_START + "57" + KEY_END);

		assertTrue(key.model().isEmpty());
		assertEquals("57", key.modelCode());
	}

	@Test
	void eachCategoryAcceptsItsModels() {
		assertEquals(Set.of(DocumentModel.NFCE), DocumentCategory.NFCE.models());
		assertEquals(Set.of(DocumentModel.NFE), DocumentCategory.NFE_SALES.models());
		assertEquals(Set.of(DocumentModel.NFE), DocumentCategory.NFE_PURCHASES.models());
		assertTrue(DocumentCategory.VOIDED.models().isEmpty());
	}
}
