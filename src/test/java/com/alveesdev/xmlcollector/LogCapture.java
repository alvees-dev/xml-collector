package com.alveesdev.xmlcollector;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LogCapture extends Handler implements AutoCloseable {

	private final Logger logger;
	private final List<LogRecord> records = new ArrayList<>();

	public LogCapture(Class<?> source) {
		this(source.getPackageName());
	}

	public LogCapture(String loggerName) {
		this.logger = Logger.getLogger(loggerName);
		logger.addHandler(this);
	}

	public synchronized List<LogRecord> records() {
		return List.copyOf(records);
	}

	public boolean has(Level level, String text) {
		return records().stream().anyMatch(entry -> entry.getLevel() == level && entry.getMessage().contains(text));
	}

	@Override
	public synchronized void publish(LogRecord entry) {
		records.add(entry);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		logger.removeHandler(this);
	}
}
