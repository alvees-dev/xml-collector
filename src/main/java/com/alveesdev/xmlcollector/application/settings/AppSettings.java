package com.alveesdev.xmlcollector.application.settings;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvType;

public record AppSettings(
		List<String> pdvDirectories,
		List<String> nfeDirectories,
		PdvType pdvType,
		String pdvModelId,
		String nfeModelId,
		OrganizeMode organizeMode,
		EmailAccount emailAccount,
		EmailDraft emailDraft
) {

	private static final List<String> ONE_EMPTY_DIRECTORY = List.of("");

	public AppSettings {
		pdvDirectories = List.copyOf(pdvDirectories);
		nfeDirectories = List.copyOf(nfeDirectories);
	}

	public static AppSettings defaults() {
		return new AppSettings(
				ONE_EMPTY_DIRECTORY,
				ONE_EMPTY_DIRECTORY,
				PdvType.PDV,
				"",
				"",
				OrganizeMode.SINGLE_FOLDER,
				DefaultEmailAccount.get(),
				EmailDraft.empty());
	}

	public int pdvCount() {
		return pdvDirectories.size();
	}

	public AppSettings withEmailAccount(EmailAccount account) {
		return new AppSettings(pdvDirectories, nfeDirectories, pdvType, pdvModelId, nfeModelId, organizeMode, account, emailDraft);
	}

	public AppSettings withEmailDraft(EmailDraft draft) {
		return new AppSettings(pdvDirectories, nfeDirectories, pdvType, pdvModelId, nfeModelId, organizeMode, emailAccount, draft);
	}
}
