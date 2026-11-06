package com.alveesdev.xmlcollector.application.settings;

public record EmailDraft(String recipient, String subject, String body) {

	public static final String DEFAULT_BODY = """
			Olá, prezados.

			Segue o arquivo XML.

			Email usado apenas para envio de arquivos. não responder.
			Em caso de dúvidas envie para suporte@unitecautomacao.com.

			Atenciosamente,""";

	public static EmailDraft empty() {
		return new EmailDraft("", "", "");
	}
}
