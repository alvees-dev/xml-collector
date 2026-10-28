package com.alveesdev.xmlcollector.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import com.alveesdev.xmlcollector.config.OrganizeMode;

public record CollectionRequest(
		List<String> directories,
		LocalDate from,
		LocalDate to,
		OrganizeMode organizeMode,
		Path archiveRoot,
		boolean groupBySeries
) {
}