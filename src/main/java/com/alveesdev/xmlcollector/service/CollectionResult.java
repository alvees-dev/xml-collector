package com.alveesdev.xmlcollector.service;

import java.util.List;

import com.alveesdev.xmlcollector.model.CopyReport;
import com.alveesdev.xmlcollector.model.MissingColumn;
import com.alveesdev.xmlcollector.model.NfceEntry;

public record CollectionResult(
		List<NfceEntry> invoices,
		List<MissingColumn> missingColumns,
		boolean groupedBySeries,
		CopyReport copyReport,
		List<String> failures
) {
}