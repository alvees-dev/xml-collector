package com.alveesdev.xmlcollector.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import com.alveesdev.xmlcollector.domain.model.CopyReport;
import com.alveesdev.xmlcollector.domain.model.MissingColumn;
import com.alveesdev.xmlcollector.domain.model.NfceEntry;
import com.alveesdev.xmlcollector.domain.model.NfceFileData;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;
import com.alveesdev.xmlcollector.domain.service.PeriodFilter;
import com.alveesdev.xmlcollector.domain.service.XmlOrganizer;
import com.alveesdev.xmlcollector.infra.xml.XmlProcessor;
import com.alveesdev.xmlcollector.infra.xml.XmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlRowReader;

public class CollectionService {

	private record PendingInvoice(String fileName, NfceFileData data) {
	}

	private record SourceOutcome(
			List<NfceEntry> entries,
			Map<Integer, List<String>> missingBySeries,
			List<String> missingFlat) {
	}

	private record Source(String label, String header, String directory, List<Path> files, boolean failed) {
	}

	private static class Counter {
		private int processed;
		private final int total;

		private Counter(int total) {
			this.total = total;
		}
	}

	public CollectionResult collect(CollectionRequest request) {
		return collect(request, ProgressListener.NONE, () -> false);
	}

	public CollectionResult collect(CollectionRequest request, ProgressListener listener, BooleanSupplier cancelled) {

		List<NfceEntry> invoices = new ArrayList<>();
		List<MissingColumn> missingColumns = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		CopyReport copyReport = new CopyReport();

		List<Source> sources = listSources(request, failures, listener, cancelled);

		int total = sources.stream().mapToInt(source -> source.files().size()).sum();
		Counter counter = new Counter(total);

		listener.onProgress(new CollectionProgress("", 0, total, ""));

		for (Source source : sources) {

			if (source.failed()) {
				addEmptyColumn(request, missingColumns, source.header());
				continue;
			}

			SourceOutcome outcome = processSource(source, request, copyReport, counter, listener, cancelled);

			invoices.addAll(outcome.entries());

			if (request.groupBySeries()) {
				outcome.missingBySeries().forEach((series, ranges) ->
						missingColumns.add(new MissingColumn("SERIE " + series, ranges)));
			} else {
				missingColumns.add(new MissingColumn(source.header(), outcome.missingFlat()));
			}
		}

		return new CollectionResult(invoices, missingColumns, request.groupBySeries(), copyReport, failures);
	}

	private List<Source> listSources(CollectionRequest request, List<String> failures, ProgressListener listener,
			BooleanSupplier cancelled) {

		List<Source> sources = new ArrayList<>();
		List<String> directories = request.directories();

		for (int index = 0; index < directories.size(); index++) {
			throwIfCancelled(cancelled);

			String directory = directories.get(index);
			String label = request.groupBySeries() ? "Diretório" : String.format("PDV %02d", index + 1);
			String header = MissingColumn.pdvHeader(index + 1);

			listener.onProgress(new CollectionProgress(label, 0, -1, ""));

			if (directory == null || directory.isBlank()) {
				failures.add(label + ": diretório não informado");
				sources.add(new Source(label, header, directory, List.of(), true));
				continue;
			}

			try {
				sources.add(new Source(label, header, directory,
						XmlProcessor.listXmlFiles(directory, request.archiveRoot()), false));
			} catch (Exception e) {
				failures.add(label + ": diretório inacessível (" + directory + ")");
				sources.add(new Source(label, header, directory, List.of(), true));
			}
		}

		return sources;
	}

	private static void addEmptyColumn(CollectionRequest request, List<MissingColumn> columns, String header) {
		if (!request.groupBySeries()) {
			columns.add(new MissingColumn(header, List.of()));
		}
	}

	private SourceOutcome processSource(Source source, CollectionRequest request, CopyReport report,
			Counter counter, ProgressListener listener, BooleanSupplier cancelled) {

		Set<String> cancelledKeys = new HashSet<>();
		List<PendingInvoice> pending = new ArrayList<>();

		for (Path file : source.files()) {
			throwIfCancelled(cancelled);

			NfceFileData data = read(file);

			boolean isCancelEvent = data != null && data.cancelledKey() != null;

			if (isCancelEvent) {
				cancelledKeys.add(data.cancelledKey());
			}

			if (PeriodFilter.isOutsidePeriod(data, request.from(), request.to())) {
				report.incrementOutOfPeriod();
			} else {
				if (data == null || data.documentDate() == null) {
					report.incrementUnreadable();
				} else {
					XmlOrganizer.copyFile(file, data.documentDate(), request.archiveRoot(), request.organizeMode(), report);
				}

				if (!isCancelEvent) {
					pending.add(new PendingInvoice(file.getFileName().toString(), data));
				}
			}

			counter.processed++;
			listener.onProgress(new CollectionProgress(
					source.label(), counter.processed, counter.total, file.getFileName().toString()));
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

	private static void throwIfCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Coleta cancelada");
		}
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