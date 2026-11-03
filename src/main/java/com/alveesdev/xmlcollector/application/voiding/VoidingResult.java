package com.alveesdev.xmlcollector.application.voiding;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;

public record VoidingResult(VoidingReceipt receipt, String archiveFailure, List<String> pdvFailures) {
}
