package com.alveesdev.xmlcollector.application.dispatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.ReportPdfWriter;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

// The same reports that go with the XML, saved as one PDF wherever the user chooses.
public class ValuesReportService {

	private final ArchivedDocuments archivedDocuments;
	private final ReportPdfWriter pdfWriter;

	public ValuesReportService(ArchivedDocuments archivedDocuments, ReportPdfWriter pdfWriter) {
		this.archivedDocuments = archivedDocuments;
		this.pdfWriter = pdfWriter;
	}

	public void writePdf(DispatchMonth month, ReportOptions options, Path file) throws IOException {

		ValuesReport company = options.anySales() ? ValuesReport.of(archivedDocuments.documentsOfTheCompanyIn(month.documents())) : ValuesReport.of(List.of());
		ValuesReport purchases = options.nfePurchases() ? ValuesReport.of(archivedDocuments.purchasesIn(month.documents())) : ValuesReport.of(List.of());

		pdfWriter.write(file, ValuesReportText.pdfLinesOf(month, company, purchases, options));
	}
}
