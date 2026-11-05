package com.alveesdev.xmlcollector.application.settings;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvType;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

public record AppSettings(
		List<String> pdvDirectories,
		List<String> nfeDirectories,
		PdvType pdvType,
		String pdvModelId,
		String nfeModelId,
		OrganizeMode organizeMode,
		SeparationMode separationMode,
		EmailAccount emailAccount,
		EmailDraft emailDraft,
		CertificateSettings certificate
) {

	private static final List<String> ONE_EMPTY_DIRECTORY = List.of("");
	private static final String DEFAULT_PDV_MODEL_ID = "pdv-1";

	public AppSettings {
		pdvDirectories = List.copyOf(pdvDirectories);
		nfeDirectories = List.copyOf(nfeDirectories);
	}

	public static AppSettings defaults() {
		return new AppSettings(
				ONE_EMPTY_DIRECTORY,
				ONE_EMPTY_DIRECTORY,
				PdvType.PDV,
				DEFAULT_PDV_MODEL_ID,
				"",
				OrganizeMode.BY_DAY,
				SeparationMode.ALL_TOGETHER,
				DefaultEmailAccount.get(),
				EmailDraft.empty(),
				CertificateSettings.empty());
	}

	public int pdvCount() {
		return pdvDirectories.size();
	}

	public AppSettings withEmailAccount(EmailAccount account) {
		return new AppSettings(pdvDirectories, nfeDirectories, pdvType, pdvModelId, nfeModelId, organizeMode, separationMode, account, emailDraft, certificate);
	}

	public AppSettings withEmailDraft(EmailDraft draft) {
		return new AppSettings(pdvDirectories, nfeDirectories, pdvType, pdvModelId, nfeModelId, organizeMode, separationMode, emailAccount, draft, certificate);
	}
}
