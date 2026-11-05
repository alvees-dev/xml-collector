package com.alveesdev.xmlcollector.infra.drive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.port.CloudStorage.UploadResult;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// The real Google libraries talking to a small server on this computer, so an update that breaks them fails here and not at a client.
class GoogleDriveStorageUploadTest {

	private static final String ACCOUNT = "envio@exemplo.com";
	private static final String ROOT_FOLDER = "Arquivos XML";
	private static final String COMPANY_FOLDER = "12345678000190";
	private static final byte[] ZIP = {1, 2, 3, 4, 5};

	@TempDir
	Path directory;

	private FakeDrive server;
	private Drive drive;
	private Path zip;

	@BeforeEach
	void start() throws IOException {
		server = new FakeDrive();
		drive = new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), request -> {
		}).setRootUrl(server.url()).setApplicationName("Teste").build();

		zip = directory.resolve("ArquivoXML.zip");
		Files.write(zip, ZIP);
	}

	@AfterEach
	void stop() {
		server.stop();
	}

	@Test
	void createsTheFoldersAndSendsTheContentOfTheFile() throws IOException {
		UploadResult result = GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip));

		assertEquals(List.of(ROOT_FOLDER, COMPANY_FOLDER), server.createdFolders);
		assertEquals(List.of("ArquivoXML.zip"), server.uploadedNames);
		assertArrayEquals(ZIP, server.uploadedContent);
		assertEquals("http://link/pasta-2", result.link());
		assertNull(result.cleanupFailure());
	}

	@Test
	void usesTheFoldersThatAlreadyExist() throws IOException {
		server.existingFolders.put(ROOT_FOLDER, "raiz");
		server.existingFolders.put(COMPANY_FOLDER, "empresa");

		UploadResult result = GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip));

		assertEquals(List.of(), server.createdFolders);
		assertEquals("https://drive.google.com/drive/folders/empresa", result.link());
	}

	@Test
	void sharesTheFolderByLink() throws IOException {
		GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip));

		assertEquals(1, server.permissions.size());
		assertTrue(server.permissions.get(0).contains("anyone"), server.permissions.get(0));
		assertTrue(server.permissions.get(0).contains("reader"), server.permissions.get(0));
	}

	@Test
	void removesTheOtherFilesOfTheFolderAndKeepsTheNewOne() throws IOException {
		server.otherFiles.add("antigo");

		GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip));

		assertEquals(List.of("antigo"), server.deleted);
	}

	@Test
	void aFailedUploadKeepsThePreviousFile() {
		server.otherFiles.add("antigo");
		server.uploadFails = true;

		assertThrows(IOException.class, () -> GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip)));

		assertEquals(List.of(), server.deleted);
	}

	@Test
	void aPreviousFileThatCouldNotBeRemovedDoesNotUndoTheUpload() throws IOException {
		server.otherFiles.add("antigo");
		server.deleteFails = true;

		UploadResult result = GoogleDriveStorage.replaceInFolder(drive, COMPANY_FOLDER, List.of(zip));

		assertNotNull(result.link());
		assertNotNull(result.cleanupFailure());
	}

	@Test
	void aSavedLoginOpensTheDriveWithoutAskingForItAgain() throws Exception {
		Path tokens = directory.resolve("tokens");
		Path credentials = directory.resolve("credenciais.json");
		Files.writeString(credentials, "{\"installed\":{\"client_id\":\"id\",\"client_secret\":\"segredo\",\"auth_uri\":\"https://exemplo/a\","
				+ "\"token_uri\":\"https://exemplo/t\",\"redirect_uris\":[\"http://localhost\"]}}");

		StoredCredential.getDefaultDataStore(new FileDataStoreFactory(tokens.toFile())).set(ACCOUNT, new StoredCredential()
				.setAccessToken("token")
				.setRefreshToken("renovar")
				.setExpirationTimeMilliseconds(System.currentTimeMillis() + 3_600_000));

		// Without the saved login it would wait for a browser, so it must not take long.
		Drive opened = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> new GoogleDriveStorage(credentials, tokens).createDrive(ACCOUNT));

		assertEquals("Coletor XML", opened.getApplicationName());
	}

	@Test
	void theLoginReceiverOpensAndClosesItsLocalPort() throws IOException {
		LocalServerReceiver receiver = new LocalServerReceiver();

		String redirect = receiver.getRedirectUri();
		receiver.stop();

		assertTrue(redirect.startsWith("http://localhost:"), redirect);
		assertFalse(redirect.endsWith(":-1"), redirect);
	}

	private static final class FakeDrive {

		private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
		private static final Pattern QUERY_NAME = Pattern.compile("name = '([^']*)'");

		private final HttpServer http;
		private final Map<String, String> existingFolders = new LinkedHashMap<>();
		private final List<String> otherFiles = new ArrayList<>();
		private final List<String> createdFolders = new ArrayList<>();
		private final List<String> uploadedNames = new ArrayList<>();
		private final List<String> permissions = new ArrayList<>();
		private final List<String> deleted = new ArrayList<>();
		private byte[] uploadedContent;
		private boolean uploadFails;
		private boolean deleteFails;
		private int folderCount;

		FakeDrive() throws IOException {
			http = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			http.createContext("/", this::answer);
			http.start();
		}

		String url() {
			return "http://localhost:" + http.getAddress().getPort() + "/";
		}

		void stop() {
			http.stop(0);
		}

		private synchronized void answer(HttpExchange exchange) throws IOException {
			String method = exchange.getRequestMethod();
			String path = exchange.getRequestURI().getPath();
			byte[] body = bodyOf(exchange);

			if (path.contains("/upload/")) {
				startUpload(exchange, body);
			} else if (path.startsWith("/sessao")) {
				uploadedContent = body;
				send(exchange, 200, "{\"id\":\"novo\"}");
			} else if (method.equals("GET") && path.endsWith("/files")) {
				list(exchange);
			} else if (method.equals("POST") && path.endsWith("/files")) {
				createFolder(exchange, body);
			} else if (method.equals("POST") && path.endsWith("/permissions")) {
				permissions.add(new String(body, StandardCharsets.UTF_8));
				send(exchange, 200, "{\"id\":\"permissao\"}");
			} else if (method.equals("DELETE")) {
				delete(exchange, path);
			} else {
				send(exchange, 404, "{}");
			}
		}

		// The Google client compresses what it sends, like a real server expects.
		private static byte[] bodyOf(HttpExchange exchange) throws IOException {
			if ("gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
				try (GZIPInputStream unzipped = new GZIPInputStream(exchange.getRequestBody())) {
					return unzipped.readAllBytes();
				}
			}

			return exchange.getRequestBody().readAllBytes();
		}

		private void startUpload(HttpExchange exchange, byte[] metadata) throws IOException {
			if (uploadFails) {
				send(exchange, 403, "{\"error\":{\"code\":403,\"message\":\"sem espaco\"}}");
				return;
			}

			uploadedNames.add(nameIn(metadata));
			exchange.getResponseHeaders().add("Location", url() + "sessao");
			send(exchange, 200, "");
		}

		private void list(HttpExchange exchange) throws IOException {
			String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
			Matcher folder = QUERY_NAME.matcher(query);

			if (folder.find()) {
				String id = existingFolders.get(folder.group(1));
				send(exchange, 200, id == null ? "{\"files\":[]}" : "{\"files\":[{\"id\":\"" + id + "\"}]}");
				return;
			}

			List<String> children = new ArrayList<>(otherFiles);
			children.add("novo");

			send(exchange, 200, "{\"files\":[" + String.join(",", children.stream().map(id -> "{\"id\":\"" + id + "\"}").toList()) + "]}");
		}

		private void createFolder(HttpExchange exchange, byte[] metadata) throws IOException {
			String name = nameIn(metadata);
			createdFolders.add(name);
			folderCount++;
			existingFolders.put(name, "pasta-" + folderCount);

			send(exchange, 200, "{\"id\":\"pasta-" + folderCount + "\",\"webViewLink\":\"http://link/pasta-" + folderCount + "\"}");
		}

		private void delete(HttpExchange exchange, String path) throws IOException {
			if (deleteFails) {
				send(exchange, 403, "{\"error\":{\"code\":403,\"message\":\"sem permissao\"}}");
				return;
			}

			deleted.add(path.substring(path.lastIndexOf('/') + 1));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		}

		private static String nameIn(byte[] metadata) {
			Matcher name = NAME.matcher(new String(metadata, StandardCharsets.UTF_8));

			return name.find() ? name.group(1) : "";
		}

		private static void send(HttpExchange exchange, int status, String json) throws IOException {
			byte[] content = json.getBytes(StandardCharsets.UTF_8);

			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, content.length == 0 ? -1 : content.length);

			if (content.length > 0) {
				exchange.getResponseBody().write(content);
			}

			exchange.close();
		}
	}
}
