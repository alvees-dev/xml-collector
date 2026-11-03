package com.alveesdev.xmlcollector.application.voiding;

import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;

public record VoidingResult(VoidingReceipt receipt, String archiveFailure) {
}
