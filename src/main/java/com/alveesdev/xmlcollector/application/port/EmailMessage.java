package com.alveesdev.xmlcollector.application.port;

import java.nio.file.Path;
import java.util.List;

public record EmailMessage(String recipient, String subject, String body, List<Path> attachments) {
}