package com.alveesdev.xmlcollector.application.connection;

import java.util.List;

// A null writable folder means nothing is ever written in this directory, so writing is not tested.
public record DirectoryTarget(String directory, List<String> folderPatterns, String writableFolder) {

	public DirectoryTarget {
		folderPatterns = List.copyOf(folderPatterns);
	}
}
