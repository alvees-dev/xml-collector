package com.alveesdev.xmlcollector.application;

import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.infra.archive.MonthZipArchiver;
import com.alveesdev.xmlcollector.infra.drive.GoogleDriveStorage;
import com.alveesdev.xmlcollector.infra.email.SmtpEmailSender;

public final class DispatchServiceFactory {

	private DispatchServiceFactory() {
	}

	public static DispatchService standard(AppPaths paths) {
		return new DispatchService(
				new MonthZipArchiver(),
				new SmtpEmailSender(),
				new GoogleDriveStorage(paths.googleCredentialsFile(), paths.googleTokensDir()));
	}
}