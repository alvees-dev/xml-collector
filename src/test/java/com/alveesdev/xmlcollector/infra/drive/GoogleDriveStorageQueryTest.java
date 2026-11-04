package com.alveesdev.xmlcollector.infra.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoogleDriveStorageQueryTest {

	private static final String FOLDER = "application/vnd.google-apps.folder";

	@Test
	void looksForTheFolderOnlyInsideItsParentAndOutOfTheTrash() {
		assertEquals("name = '12345678000190' and mimeType = '" + FOLDER + "' and 'pai' in parents and trashed = false",
				GoogleDriveStorage.folderQuery("12345678000190", "pai"));
	}

	@Test
	void looksOnlyForTheFilesInsideTheFolder() {
		assertEquals("'abc' in parents and mimeType != '" + FOLDER + "' and trashed = false",
				GoogleDriveStorage.childrenQuery("abc"));
	}

	@Test
	void escapesTheNameOfTheFolder() {
		assertEquals("name = 'a\\'b\\\\c' and mimeType = '" + FOLDER + "' and 'pai' in parents and trashed = false",
				GoogleDriveStorage.folderQuery("a'b\\c", "pai"));
	}
}
