package com.alveesdev.xmlcollector.application.collection;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public record CollectionRequest(
		List<String> directories,
		DocumentCategory category,
		DateRange period,
		OrganizeMode organizeMode,
		Path archiveRoot,
		List<String> folderPatterns,
		Predicate<XmlDocument.Invoice> invoiceFilter
) {

	public CollectionRequest(List<String> directories, DocumentCategory category, DateRange period,
			OrganizeMode organizeMode, Path archiveRoot) {
		this(directories, category, period, organizeMode, archiveRoot, List.of(), invoice -> true);
	}
}
