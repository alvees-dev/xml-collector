package com.alveesdev.xmlcollector.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public sealed interface XmlDocument {

	Optional<String> identity();

	record Nfce(
			AccessKey key,
			int series,
			int number,
			OffsetDateTime issuedAt,
			BigDecimal totalValue) implements XmlDocument {

		public LocalDate date() {
			return issuedAt.toLocalDate();
		}

		@Override
		public Optional<String> identity() {
			return Optional.of(key.value());
		}
	}

	record CancelEvent(
			String eventId,
			AccessKey cancelledKey,
			OffsetDateTime eventAt) implements XmlDocument {

		public LocalDate date() {
			return eventAt.toLocalDate();
		}

		@Override
		public Optional<String> identity() {
			return Optional.of(eventId);
		}
	}

	record Ignored(String reason) implements XmlDocument {

		@Override
		public Optional<String> identity() {
			return Optional.empty();
		}
	}

	record Unreadable(String reason) implements XmlDocument {

		@Override
		public Optional<String> identity() {
			return Optional.empty();
		}
	}
}