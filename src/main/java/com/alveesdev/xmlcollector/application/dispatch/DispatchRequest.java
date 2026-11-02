package com.alveesdev.xmlcollector.application.dispatch;

import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;

public record DispatchRequest(
		List<Path> monthDirectories,
		EmailAccount account,
		String recipient,
		String subject,
		String body
) {
}