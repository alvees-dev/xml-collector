package com.alveesdev.xmlcollector.infra.drive;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.alveesdev.xmlcollector.application.port.CloudStorage;
import com.alveesdev.xmlcollector.application.port.DispatchException;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

public class GoogleDriveStorage implements CloudStorage {

	private static final String APPLICATION_NAME = "Coletor XML";
	private static final String LOGIN_HINT_PARAMETER = "login_hint";
	private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
	private static final String ZIP_MIME_TYPE = "application/zip";
	private static final String FOLDER_LINK_PREFIX = "https://drive.google.com/drive/folders/";
	private static final int MAX_PAGE_SIZE = 1000;
	private static final String ROOT_FOLDER_NAME = "Arquivos XML";
	private static final String DRIVE_ROOT = "root";

	private final Path credentialsFile;
	private final Path tokensDirectory;

	public GoogleDriveStorage(Path credentialsFile, Path tokensDirectory) {
		this.credentialsFile = credentialsFile;
		this.tokensDirectory = tokensDirectory;
	}

	@Override
	public UploadResult replaceInSharedFolder(String accountEmail, String folderName, List<Path> files)
			throws DispatchException {

		if (!Files.isRegularFile(credentialsFile)) {
			throw new DispatchException("O arquivo de credenciais do Google Drive não foi encontrado em:\n"
					+ credentialsFile);
		}

		try {
			Drive drive = createDrive(accountEmail);

			File root = findOrCreateFolder(drive, ROOT_FOLDER_NAME, DRIVE_ROOT);
			File folder = findOrCreateFolder(drive, folderName, root.getId());
			Set<String> uploaded = new HashSet<>();

			for (Path file : files) {
				File metadata = new File();
				metadata.setName(file.getFileName().toString());
				metadata.setParents(List.of(folder.getId()));

				FileContent content = new FileContent(ZIP_MIME_TYPE, file.toFile());
				uploaded.add(drive.files().create(metadata, content).setFields("id").execute().getId());
			}

			Permission anyoneWithLink = new Permission();
			anyoneWithLink.setType("anyone");
			anyoneWithLink.setRole("reader");
			drive.permissions().create(folder.getId(), anyoneWithLink).execute();

			String link = folder.getWebViewLink() != null ? folder.getWebViewLink() : FOLDER_LINK_PREFIX + folder.getId();

			// Only now, so a failed upload never leaves the folder without its previous file.
			return new UploadResult(link, removeOthers(drive, folder.getId(), uploaded));

		} catch (IOException | GeneralSecurityException e) {
			throw new DispatchException("Falha ao enviar os arquivos para o Google Drive: " + e.getMessage(), e);
		}
	}

	private static File findOrCreateFolder(Drive drive, String folderName, String parentId) throws IOException {

		List<File> found = drive.files().list()
				.setQ(folderQuery(folderName, parentId))
				.setSpaces("drive")
				.setFields("files(id, webViewLink)")
				.execute()
				.getFiles();

		if (!found.isEmpty()) {
			return found.get(0);
		}

		File metadata = new File();
		metadata.setName(folderName);
		metadata.setMimeType(FOLDER_MIME_TYPE);
		metadata.setParents(List.of(parentId));

		return drive.files().create(metadata).setFields("id, webViewLink").execute();
	}

	// A deleted file skips the trash, because the trash still counts against the storage of the account.
	private static String removeOthers(Drive drive, String folderId, Set<String> kept) {
		try {
			List<File> children = drive.files().list()
					.setQ(childrenQuery(folderId))
					.setSpaces("drive")
					.setPageSize(MAX_PAGE_SIZE)
					.setFields("files(id)")
					.execute()
					.getFiles();

			for (File child : children) {
				if (!kept.contains(child.getId())) {
					drive.files().delete(child.getId()).execute();
				}
			}

			return null;

		} catch (IOException e) {
			return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		}
	}

	static String folderQuery(String folderName, String parentId) {
		return "name = '" + escaped(folderName) + "' and mimeType = '" + FOLDER_MIME_TYPE
				+ "' and '" + escaped(parentId) + "' in parents and trashed = false";
	}

	static String childrenQuery(String folderId) {
		return "'" + escaped(folderId) + "' in parents and mimeType != '" + FOLDER_MIME_TYPE + "' and trashed = false";
	}

	private static String escaped(String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'");
	}

	private Drive createDrive(String accountEmail) throws IOException, GeneralSecurityException {

		NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
		JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

		GoogleClientSecrets secrets;

		try (Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			secrets = GoogleClientSecrets.load(jsonFactory, reader);
		}

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				transport, jsonFactory, secrets, List.of(DriveScopes.DRIVE_FILE))
				.setDataStoreFactory(new FileDataStoreFactory(tokensDirectory.toFile()))
				.setAccessType("offline")
				.build();

		AuthorizationCodeInstalledApp installedApp = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()) {

			@Override
			protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
				authorizationUrl.set(LOGIN_HINT_PARAMETER, accountEmail);
				super.onAuthorization(authorizationUrl);
			}
		};

		Credential credential = installedApp.authorize(accountEmail);

		return new Drive.Builder(transport, jsonFactory, credential)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}
}