package com.alveesdev.xmlcollector.ui.model;

import java.util.logging.Level;
import java.util.logging.Logger;

// What the user did and how it ended, in the words of the screen.
public final class ActivityLog {

	private static final Logger LOG = Logger.getLogger(ActivityLog.class.getName());

	private ActivityLog() {
	}

	public static void record(String activity) {
		record(Level.INFO, activity);
	}

	public static void record(Level level, String activity) {
		LOG.log(level, activity.strip().replaceAll("\\s*\\R\\s*", " | "));
	}
}
