package com.alveesdev.xmlcollector.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.IntStream;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public class MissingTableModel {

	private final List<String> headers;
	private final List<List<String>> rows;
	private final boolean seriesBased;

	private MissingTableModel(List<String> headers, List<List<String>> rows, boolean seriesBased) {
		this.headers = headers;
		this.rows = rows;
		this.seriesBased = seriesBased;
	}

	public static MissingTableModel forPdvs(int pdvCount) {
		List<String> headers = IntStream.range(0, pdvCount)
				.mapToObj(SourceLabels::header)
				.toList();

		List<List<String>> columns = IntStream.range(0, pdvCount)
				.mapToObj(index -> List.<String>of())
				.toList();

		return build(headers, columns, false);
	}

	public static MissingTableModel fromResult(CollectionResult result) {
		return fromMissing(result.missingBySeries());
	}

	public static MissingTableModel fromMissing(SortedMap<Integer, List<NumberRange>> missingBySeries) {

		List<String> headers = new ArrayList<>();
		List<List<String>> columns = new ArrayList<>();

		missingBySeries.forEach((series, ranges) -> {
			headers.add("SERIE " + series);
			columns.add(ranges.stream().map(NumberRange::text).toList());
		});

		return build(headers, columns, true);
	}

	private static MissingTableModel build(List<String> headers, List<List<String>> columns, boolean seriesBased) {

		int maxRows = columns.stream().mapToInt(List::size).max().orElse(0);
		List<List<String>> rows = new ArrayList<>();

		for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
			List<String> row = new ArrayList<>();

			for (List<String> column : columns) {
				row.add(rowIndex < column.size() ? column.get(rowIndex) : "");
			}

			rows.add(row);
		}

		return new MissingTableModel(headers, rows, seriesBased);
	}

	public List<String> getHeaders() {
		return headers;
	}

	public List<List<String>> getRows() {
		return rows;
	}

	public boolean isSeriesBased() {
		return seriesBased;
	}
}