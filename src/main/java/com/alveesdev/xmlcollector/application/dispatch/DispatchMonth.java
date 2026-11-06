package com.alveesdev.xmlcollector.application.dispatch;

import java.nio.file.Path;
import java.time.YearMonth;

import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

// The folder whose notes go out, and where its zip is kept: always in the archive, never in the folder of another system.
public record DispatchMonth(Path documents, YearMonth month, Source source, Path zipFolder) {

	public enum Source {
		COLLECTED("p"),
		SERVER("s");

		private final String mark;

		Source(String mark) {
			this.mark = mark;
		}

		public String mark() {
			return mark;
		}
	}

	public static DispatchMonth collected(Path archiveRoot, YearMonth month) {
		return new DispatchMonth(ArchiveLayout.monthDirectory(archiveRoot, month.atDay(1)), month, Source.COLLECTED,
				ArchiveLayout.zipFolder(archiveRoot, month));
	}

	public static DispatchMonth server(Path serverMonth, Path archiveRoot, YearMonth month) {
		return new DispatchMonth(serverMonth, month, Source.SERVER, ArchiveLayout.zipFolder(archiveRoot, month));
	}

	// As in "Janeiro_2026", the name of the month inside the zip and in the messages.
	public String name() {
		return ArchiveLayout.monthYearName(month.atDay(1));
	}

	// As in "Janeiro de 2026", for the report.
	public String label() {
		return ArchiveLayout.monthNames().get(month.getMonthValue() - 1) + " de " + month.getYear();
	}

	// As in "Janeiro 2026", for the names of the reports.
	public String monthAndYear() {
		return ArchiveLayout.monthNames().get(month.getMonthValue() - 1) + " " + month.getYear();
	}

	public String zipLabel() {
		return ArchiveLayout.zipLabel(month, source.mark());
	}
}
