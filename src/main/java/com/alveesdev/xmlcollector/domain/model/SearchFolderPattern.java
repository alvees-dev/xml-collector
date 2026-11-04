package com.alveesdev.xmlcollector.domain.model;

public final class SearchFolderPattern {

	public static final String SEPARATOR = "/";
	public static final String ANY_DEPTH = "**";
	public static final String YEAR_MONTH = "{AAAAMM}";
	public static final String YEAR = "{AAAA}";
	public static final String MONTH_NAME_YEAR = "{MES_AAAA}";
	public static final String OPTIONAL_PREFIX = "?";
	public static final String CNPJ = "{CNPJ}";

	private SearchFolderPattern() {
	}

	public static String fixedPrefix(String pattern) {
		int firstName = pattern.indexOf('{');
		String fixed = firstName < 0 ? pattern : pattern.substring(0, firstName);

		return fixed.endsWith(SEPARATOR) ? fixed.substring(0, fixed.length() - SEPARATOR.length()) : fixed;
	}
}
