package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ReportPdfWriter {

	// A heading starts a part of the report and stands out from the lines under it.
	record Line(String text, boolean heading) {
	}

	void write(Path file, List<Line> lines) throws IOException;
}
