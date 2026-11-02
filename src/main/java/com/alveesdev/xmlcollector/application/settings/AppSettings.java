package com.alveesdev.xmlcollector.application.settings;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

public record AppSettings(
		List<String> pdvDirectories,
		List<String> nfeSalesDirectories,
		List<String> nfePurchaseDirectories,
		OrganizeMode organizeMode,
		EmailAccount emailAccount,
		EmailDraft emailDraft
) {

	private static final List<String> ONE_EMPTY_DIRECTORY = List.of("");

	public AppSettings {
		pdvDirectories = List.copyOf(pdvDirectories);
		nfeSalesDirectories = List.copyOf(nfeSalesDirectories);
		nfePurchaseDirectories = List.copyOf(nfePurchaseDirectories);
	}

	public static AppSettings defaults() {
		return new AppSettings(
				ONE_EMPTY_DIRECTORY,
				ONE_EMPTY_DIRECTORY,
				ONE_EMPTY_DIRECTORY,
				OrganizeMode.SINGLE_FOLDER,
				DefaultEmailAccount.get(),
				EmailDraft.empty());
	}

	public int pdvCount() {
		return pdvDirectories.size();
	}

	public AppSettings withEmailDraft(EmailDraft draft) {
		return new AppSettings(pdvDirectories, nfeSalesDirectories, nfePurchaseDirectories, organizeMode, emailAccount, draft);
	}
}
