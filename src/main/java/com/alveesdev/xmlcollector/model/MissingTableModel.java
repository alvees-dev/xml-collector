package com.alveesdev.xmlcollector.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
		List<MissingColumn> columns = IntStream.rangeClosed(1, pdvCount)
				.mapToObj(number -> new MissingColumn(MissingColumn.pdvHeader(number), List.of()))
				.toList();

		return fromColumns(columns, false);
	}

	public static MissingTableModel fromColumns(List<MissingColumn> columns, boolean seriesBased) {
		List<String> headers = columns.stream().map(MissingColumn::header).toList();

		int maxRows = columns.stream().mapToInt(column -> column.ranges().size()).max().orElse(0);

		List<List<String>> rows = new ArrayList<>();

		for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
			List<String> row = new ArrayList<>();

			for (MissingColumn column : columns) {
				row.add(rowIndex < column.ranges().size() ? column.ranges().get(rowIndex) : "");
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