package com.alveesdev.xmlcollector.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.model.CopyReport;
import com.alveesdev.xmlcollector.model.MissingColumn;
import com.alveesdev.xmlcollector.model.NfceEntry;
import com.alveesdev.xmlcollector.model.NfceFileData;
import com.alveesdev.xmlcollector.xml.XmlProcessor;
import com.alveesdev.xmlcollector.xml.XmlReader;
import com.alveesdev.xmlcollector.xml.XmlRowReader;

public class CollectionService {

	private record PendingInvoice(String fileName, NfceFileData data) {
	}

	private record SourceOutcome(
			List<NfceEntry> entries,
			Map<Integer, List<String>> missingBySeries,
			List<String> missingFlat) {
	}

	public CollectionResult collect(CollectionRequest request) {

		List<NfceEntry> invoices = new ArrayList<>();
		List<MissingColumn> missingColumns = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		CopyReport copyReport = new CopyReport();

		List<String> directories = request.directories();

		for (int index = 0; index < directories.size(); index++) {
			String directory = directories.get(index);
			String label = request.groupBySeries() ? "Diretório" : String.format("PDV %02d", index + 1);
			String header = MissingColumn.pdvHeader(index + 1);

			if (directory == null || directory.isBlank()) {
				failures.add(label + ": diretório não informado");
				addEmptyColumn(request, missingColumns, header);
				continue;
			}

			try {
				SourceOutcome outcome = processSource(directory, request, copyReport);

				invoices.addAll(outcome.entries());

				if (request.groupBySeries()) {
					outcome.missingBySeries().forEach((series, ranges) ->
							missingColumns.add(new MissingColumn("SERIE " + series, ranges)));
				} else {
					missingColumns.add(new MissingColumn(header, outcome.missingFlat()));
				}

			} catch (Exception e) {
				failures.add(label + ": diretório inacessível (" + directory + ")");
				addEmptyColumn(request, missingColumns, header);
			}
		}

		return new CollectionResult(invoices, missingColumns, request.groupBySeries(), copyReport, failures);
	}

	private static void addEmptyColumn(CollectionRequest request, List<MissingColumn> columns, String header) {
		if (!request.groupBySeries()) {
			columns.add(new MissingColumn(header, List.of()));
		}
	}

	private SourceOutcome processSource(String directory, CollectionRequest request, CopyReport report)
			throws IOException {

		List<Path> files = XmlProcessor.listXmlFiles(directory, request.archiveRoot());

		Set<String> cancelledKeys = new HashSet<>();
		List<PendingInvoice> pending = new ArrayList<>();

		for (Path file : files) {
			NfceFileData data = read(file);

			boolean isCancelEvent = data != null && data.cancelledKey() != null;

			if (isCancelEvent) {
				cancelledKeys.add(data.cancelledKey());
			}

			if (PeriodFilter.isOutsidePeriod(data, request.from(), request.to())) {
				report.incrementOutOfPeriod();
				continue;
			}

			if (data == null || data.documentDate() == null) {
				report.incrementUnreadable();
			} else {
				XmlOrganizer.copyFile(file, data.documentDate(), request.archiveRoot(), request.organizeMode(), report);
			}

			if (!isCancelEvent) {
				pending.add(new PendingInvoice(file.getFileName().toString(), data));
			}
		}

		List<NfceEntry> entries = new ArrayList<>();
		List<NfceFileData> periodData = new ArrayList<>();

		for (PendingInvoice invoice : pending) {
			entries.add(toEntry(invoice, cancelledKeys));

			if (invoice.data() != null) {
				periodData.add(invoice.data());
			}
		}

		return new SourceOutcome(
				entries,
				MissingNumberFinder.findMissingBySeries(periodData),
				MissingNumberFinder.findMissing(periodData));
	}

	private static NfceFileData read(Path file) {
		try {
			return XmlRowReader.read(XmlReader.loadXML(file.toFile()));
		} catch (Exception e) {
			return null;
		}
	}

	private static NfceEntry toEntry(PendingInvoice invoice, Set<String> cancelledKeys) {
		NfceFileData data = invoice.data();

		if (data == null) {
			return new NfceEntry(null, null, null, null, invoice.fileName(), "Erro na Leitura");
		}

		if (data.accessKey() == null) {
			return new NfceEntry(data.number(), data.series(), data.totalValue(), data.emissionDate(),
					invoice.fileName(), "Erro na Leitura");
		}

		String status = cancelledKeys.contains(data.accessKey()) ? "Cancelada" : "Enviada";

		return new NfceEntry(data.number(), data.series(), data.totalValue(), data.emissionDate(),
				data.accessKey(), status);
	}
}