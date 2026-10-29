package com.alveesdev.xmlcollector.application;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.CopyReport;
import com.alveesdev.xmlcollector.domain.model.MissingColumn;
import com.alveesdev.xmlcollector.domain.model.NfceEntry;

public record CollectionResult(
		List<NfceEntry> invoices,
		List<MissingColumn> missingColumns,
		boolean groupedBySeries,
		CopyReport copyReport,
		List<String> failures
) {
}