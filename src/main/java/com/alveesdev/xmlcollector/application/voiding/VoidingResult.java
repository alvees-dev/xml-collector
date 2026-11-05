package com.alveesdev.xmlcollector.application.voiding;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;

// The unsaved XML is only filled in when no copy of an approved voiding reached the disk.
public record VoidingResult(VoidingReceipt receipt, String archiveFailure, List<String> pdvFailures, String unsavedXml) {
}
