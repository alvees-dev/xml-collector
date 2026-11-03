package com.alveesdev.xmlcollector.application.collection;

import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

public record CollectionRequest(
		List<String> directories,
		DocumentCategory category,
		DateRange period,
		OrganizeMode organizeMode,
		Path archiveRoot,
		List<String> folderPatterns,
		CollectionTarget target,
		SeparationMode separationMode
) {

	public CollectionRequest(List<String> directories, DocumentCategory category, DateRange period,
			OrganizeMode organizeMode, Path archiveRoot) {
		this(directories, category, period, organizeMode, archiveRoot, List.of(), CollectionTarget.ALL);
	}

	public CollectionRequest(List<String> directories, DocumentCategory category, DateRange period,
			OrganizeMode organizeMode, Path archiveRoot, List<String> folderPatterns, CollectionTarget target) {
		this(directories, category, period, organizeMode, archiveRoot, folderPatterns, target, SeparationMode.ALL_TOGETHER);
	}
}
