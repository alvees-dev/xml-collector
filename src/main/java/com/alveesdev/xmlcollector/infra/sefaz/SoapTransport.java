package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.net.URI;

interface SoapTransport {

	String post(URI url, String envelope) throws IOException, InterruptedException;
}
