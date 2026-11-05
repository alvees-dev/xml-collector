package com.alveesdev.xmlcollector.infra.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.alveesdev.xmlcollector.application.port.Failures;

public final class FileLog {

	private static final String FILE_PATTERN = "coletor.%g.log";
	private static final int MAX_BYTES_PER_FILE = 2 * 1024 * 1024;
	private static final int MAX_FILES = 5;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String REASON_SEPARATOR = " | ";

	private FileLog() {
	}

	// The packaged program has no console, so this file is the only record of what went wrong on the computer of a client.
	public static Handler start(Path directory) throws IOException {
		Files.createDirectories(directory);

		// A percent sign would be read as part of the pattern.
		String pattern = directory.toString().replace("%", "%%") + directory.getFileSystem().getSeparator() + FILE_PATTERN;

		FileHandler handler = new FileHandler(pattern, MAX_BYTES_PER_FILE, MAX_FILES, true);
		handler.setEncoding(StandardCharsets.UTF_8.name());
		handler.setFormatter(new LineFormatter());

		Logger.getLogger("").addHandler(handler);

		return handler;
	}

	private static final class LineFormatter extends Formatter {

		@Override
		public String format(LogRecord entry) {
			String time = TIME_FORMAT.format(entry.getInstant().atZone(ZoneId.systemDefault()));
			String line = time + " " + labelOf(entry.getLevel()) + " " + formatMessage(entry);
			Throwable error = entry.getThrown();

			if (error == null) {
				return line + System.lineSeparator();
			}

			return Failures.isDefect(error)
					? line + System.lineSeparator() + stackOf(error)
					: line + REASON_SEPARATOR + reasonsOf(error) + System.lineSeparator();
		}

		private static String labelOf(Level level) {
			if (level == Level.SEVERE) {
				return "ERRO";
			}

			return level == Level.WARNING ? "AVISO" : level.getName();
		}

		// The name of the exception stays because the message of some is only a path.
		private static String reasonsOf(Throwable error) {
			return Failures.chainOf(error)
					.map(link -> link.getClass().getSimpleName() + ": " + Failures.describe(link).replaceAll("\\R+", " "))
					.distinct()
					.collect(Collectors.joining(REASON_SEPARATOR));
		}

		private static String stackOf(Throwable error) {
			StringWriter text = new StringWriter();
			error.printStackTrace(new PrintWriter(text, true));

			return text.toString();
		}
	}
}
