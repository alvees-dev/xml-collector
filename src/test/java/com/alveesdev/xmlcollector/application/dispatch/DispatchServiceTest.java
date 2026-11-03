package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.DirectoryCompressor;
import com.alveesdev.xmlcollector.application.port.EmailMessage;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;

class DispatchServiceTest {

	private static final Path MONTH = Path.of("arquivos", "Janeiro_2026");
	private static final EmailAccount ACCOUNT = new EmailAccount(
			"smtp.gmail.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-de-teste");

	private final List<String> zipNames = new ArrayList<>();
	private final List<EmailMessage> sent = new ArrayList<>();

	private final DirectoryCompressor compressor = (directory, zipName) -> {
		zipNames.add(zipName);
		return Optional.of(new DirectoryCompressor.CompressedFile(directory.resolveSibling(zipName + ".zip"), 10));
	};

	@Test
	void namesTheZipWithTheCnpjTheMonthAndTheYear() throws Exception {
		dispatch(Set.of("12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_Janeiro_2026"), zipNames);
		assertEquals(List.of(MONTH.resolveSibling("ArquivoXML_12345678000190_Janeiro_2026.zip")), sent.get(0).attachments());
	}

	@Test
	void namesEveryCnpjWhenTheMonthHasMoreThanOne() throws Exception {
		dispatch(Set.of("98765432000110", "12345678000190"));

		assertEquals(List.of("ArquivoXML_12345678000190_98765432000110_Janeiro_2026"), zipNames);
	}

	@Test
	void leavesTheCnpjOutWhenNoneIsFound() throws Exception {
		dispatch(Set.of());

		assertEquals(List.of("ArquivoXML_Janeiro_2026"), zipNames);
	}

	private void dispatch(Set<String> cnpjs) throws Exception {
		DispatchService service = new DispatchService(
				directory -> cnpjs,
				compressor,
				(account, message) -> sent.add(message),
				(accountEmail, folderName, files) -> "link");

		service.dispatch(new DispatchRequest(List.of(MONTH), ACCOUNT, "destino@exemplo.com", "", ""));
	}
}
