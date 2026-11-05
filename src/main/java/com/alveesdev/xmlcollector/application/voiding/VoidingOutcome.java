package com.alveesdev.xmlcollector.application.voiding;

import java.util.List;

import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public record VoidingOutcome(VoidingRequest request, Stage stage, String detail, String archiveFailure, List<String> pdvFailures, String unsavedXml) {

	public VoidingOutcome(VoidingRequest request, Stage stage, String detail) {
		this(request, stage, detail, null, List.of(), null);
	}
}
