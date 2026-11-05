package com.alveesdev.xmlcollector.application.dispatch;

public record ReportOptions(boolean nfce, boolean nfeSales, boolean nfePurchases) {

	public static final ReportOptions STANDARD = new ReportOptions(true, false, false);

	public boolean anySelected() {
		return nfce || nfeSales || nfePurchases;
	}

	public boolean anySales() {
		return nfce || nfeSales;
	}
}
