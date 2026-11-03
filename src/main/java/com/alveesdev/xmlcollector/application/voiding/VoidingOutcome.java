package com.alveesdev.xmlcollector.application.voiding;

import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public record VoidingOutcome(VoidingRequest request, Stage stage, String detail, String archiveFailure) {
}
