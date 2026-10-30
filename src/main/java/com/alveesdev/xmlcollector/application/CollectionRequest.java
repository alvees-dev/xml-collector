package com.alveesdev.xmlcollector.application;

import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public record CollectionRequest(
		List<String> directories,
		DateRange period,
		OrganizeMode organizeMode,
		Path archiveRoot
) {
}