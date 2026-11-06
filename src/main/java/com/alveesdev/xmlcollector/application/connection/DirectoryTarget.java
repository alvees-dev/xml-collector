package com.alveesdev.xmlcollector.application.connection;

import java.util.List;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;

// A null writable folder means nothing is ever written in this directory, so writing is not tested.
public record DirectoryTarget(String directory, List<String> folderPatterns, String writableFolder) {

	public DirectoryTarget {
		folderPatterns = List.copyOf(folderPatterns);
	}

	// What a PDV must have for its search model: the folders of the collection and of the search, and the voiding folder to write in.
	public static DirectoryTarget ofPdv(String directory, PdvSearchModel model) {

		List<String> folders = Stream.concat(model.collectionFolders().stream(), model.missingFolders().stream())
				.distinct()
				.toList();

		return new DirectoryTarget(directory, folders, SearchFolderPattern.fixedPrefix(model.voidingFolder()));
	}
}
