package com.alveesdev.xmlcollector.application.port;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;

public interface EmailSender {

	void send(EmailAccount account, EmailMessage message) throws DispatchException;
}