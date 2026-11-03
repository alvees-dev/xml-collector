package com.alveesdev.xmlcollector.domain.model;

public final class SearchFolderPattern {

	public static final String SEPARATOR = "/";
	public static final String ANY_DEPTH = "**";
	public static final String YEAR_MONTH = "{AAAAMM}";
	public static final String YEAR = "{AAAA}";
	public static final String MONTH_NAME_YEAR = "{MES_AAAA}";
	public static final String OPTIONAL_PREFIX = "?";

	private SearchFolderPattern() {
	}
}
