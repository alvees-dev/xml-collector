package com.alveesdev.xmlcollector.model;

import java.time.LocalDate;

public record NfceFileData(
        String number,
        String series,
        String totalValue,
        String emissionDate,
        String cancelledKey,
        LocalDate documentDate,
        String accessKey
) {
}