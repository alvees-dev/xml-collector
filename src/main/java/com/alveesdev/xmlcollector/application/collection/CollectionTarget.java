package com.alveesdev.xmlcollector.application.collection;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public record CollectionTarget(
		IntPredicate source,
		Predicate<String> fileName,
		Predicate<XmlDocument.Invoice> invoice
) {

	public static final CollectionTarget ALL = new CollectionTarget(index -> true, name -> true, invoice -> true);

	private static final Pattern KEY_IN_NAME = Pattern.compile("(?<![0-9])[0-9]{44}(?![0-9])");

	public static CollectionTarget missing(Map<Integer, List<NumberRange>> wantedBySeries, IntPredicate source, String fileSuffix) {
		String suffix = fileSuffix.toLowerCase(Locale.ROOT);

		return new CollectionTarget(
				source,
				name -> name.toLowerCase(Locale.ROOT).endsWith(suffix) && nameMayBeWanted(name, wantedBySeries),
				invoice -> isWanted(wantedBySeries, invoice.series(), invoice.number()));
	}

	private static boolean nameMayBeWanted(String name, Map<Integer, List<NumberRange>> wantedBySeries) {
		Matcher matcher = KEY_IN_NAME.matcher(name);

		if (!matcher.find()) {
			return true;
		}

		AccessKey key = new AccessKey(matcher.group());
		return isWanted(wantedBySeries, key.series(), key.number());
	}

	private static boolean isWanted(Map<Integer, List<NumberRange>> wantedBySeries, int series, int number) {
		return wantedBySeries.getOrDefault(series, List.of()).stream()
				.anyMatch(range -> range.start() <= number && number <= range.end());
	}
}
