package com.alveesdev.xmlcollector.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public record ValuesReport(List<Section> sections, int unreadableFiles) {

	// A null first and last number means the series only has voided numbers in this month.
	public record Section(
			String cnpj,
			String modelCode,
			int series,
			Integer firstNumber,
			Integer lastNumber,
			List<Integer> cancelledNumbers,
			List<NumberRange> voidedRanges,
			int sales,
			BigDecimal salesValue) {
	}

	private static final int VOIDING_CNPJ_START = 6;
	private static final int VOIDING_CNPJ_END = 20;
	private static final int VOIDING_MODEL_END = 22;

	private record SectionKey(String cnpj, String modelCode, int series) {
	}

	private static class Builder {
		private final Map<AccessKey, XmlDocument.Invoice> invoices = new HashMap<>();
		private final List<NumberRange> voided = new ArrayList<>();
	}

	public ValuesReport {
		sections = List.copyOf(sections);
	}

	public static ValuesReport of(Collection<XmlDocument> documents) {

		Map<SectionKey, Builder> builders = new TreeMap<>(Comparator.comparing(SectionKey::cnpj)
				.thenComparing(SectionKey::modelCode)
				.thenComparingInt(SectionKey::series));

		Set<AccessKey> cancelled = new HashSet<>();
		Set<String> seenVoidings = new HashSet<>();
		int unreadable = 0;

		for (XmlDocument document : documents) {
			if (document instanceof XmlDocument.Invoice invoice) {
				AccessKey key = invoice.key();
				builders.computeIfAbsent(new SectionKey(key.cnpj(), key.modelCode(), invoice.series()), ignored -> new Builder())
						.invoices.put(key, invoice);
			} else if (document instanceof XmlDocument.CancelEvent event) {
				cancelled.add(event.cancelledKey());
			} else if (document instanceof XmlDocument.Voiding voiding) {
				if (seenVoidings.add(voiding.id())) {
					builders.computeIfAbsent(keyOf(voiding), ignored -> new Builder()).voided.add(voiding.range());
				}
			} else if (document instanceof XmlDocument.Unreadable) {
				unreadable++;
			}
		}

		List<Section> sections = new ArrayList<>();
		builders.forEach((key, builder) -> sections.add(sectionOf(key, builder, cancelled)));

		return new ValuesReport(sections, unreadable);
	}

	public int totalSales() {
		return sections.stream().mapToInt(Section::sales).sum();
	}

	public BigDecimal totalValue() {
		return sections.stream().map(Section::salesValue).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// The voiding has no fields for these, but they are part of its id.
	private static SectionKey keyOf(XmlDocument.Voiding voiding) {
		String id = voiding.id();

		return new SectionKey(id.substring(VOIDING_CNPJ_START, VOIDING_CNPJ_END), id.substring(VOIDING_CNPJ_END, VOIDING_MODEL_END),
				voiding.series());
	}

	private static Section sectionOf(SectionKey key, Builder builder, Set<AccessKey> cancelledKeys) {

		List<XmlDocument.Invoice> invoices = builder.invoices.values().stream()
				.sorted(Comparator.comparingInt(XmlDocument.Invoice::number))
				.toList();

		List<Integer> cancelled = invoices.stream()
				.filter(invoice -> cancelledKeys.contains(invoice.key()))
				.map(XmlDocument.Invoice::number)
				.toList();

		List<XmlDocument.Invoice> sold = invoices.stream()
				.filter(invoice -> !cancelledKeys.contains(invoice.key()))
				.toList();

		BigDecimal value = sold.stream().map(XmlDocument.Invoice::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add);

		List<NumberRange> voided = builder.voided.stream()
				.sorted(Comparator.comparingInt(NumberRange::start))
				.toList();

		return new Section(key.cnpj(), key.modelCode(), key.series(),
				invoices.isEmpty() ? null : invoices.get(0).number(),
				invoices.isEmpty() ? null : invoices.get(invoices.size() - 1).number(),
				cancelled, voided, sold.size(), value);
	}
}
