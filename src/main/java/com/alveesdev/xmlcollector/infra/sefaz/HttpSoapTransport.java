package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

final class HttpSoapTransport implements SoapTransport {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
	private static final String CONTENT_TYPE = "application/soap+xml; charset=utf-8";
	private static final int HTTP_OK = 200;
	private static final int HTTP_SERVER_ERROR = 500;

	private final HttpClient client;

	HttpSoapTransport(A1Certificate certificate) throws GeneralSecurityException, IOException {

		KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagers.init(certificate.keyStore(), certificate.password());

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keyManagers.getKeyManagers(), SefazTrust.trustManagers(), null);

		this.client = HttpClient.newBuilder()
				.sslContext(context)
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
	}

	@Override
	public String post(URI url, String envelope) throws IOException, InterruptedException {

		HttpRequest request = HttpRequest.newBuilder(url)
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", CONTENT_TYPE)
				.POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		// A SOAP fault arrives as HTTP 500 with a body that explains the failure.
		if (response.statusCode() != HTTP_OK && response.statusCode() != HTTP_SERVER_ERROR) {
			throw new IOException("A Sefaz respondeu com HTTP " + response.statusCode());
		}

		return response.body();
	}
}
