package com.alveesdev.xmlcollector.application.dispatch;

import java.util.List;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;

public record DispatchRequest(
		List<DispatchMonth> months,
		EmailAccount account,
		String recipient,
		String subject,
		String body,
		boolean reuseExistingZip,
		ReportOptions reports
) {
}
