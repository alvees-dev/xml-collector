package com.alveesdev.xmlcollector.application;

import com.alveesdev.xmlcollector.application.settings.EmailAccount;

public interface EmailSender {

	void send(EmailAccount account, EmailMessage message) throws DispatchException;
}