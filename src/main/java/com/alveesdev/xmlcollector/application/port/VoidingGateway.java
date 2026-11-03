package com.alveesdev.xmlcollector.application.port;

import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public interface VoidingGateway {

	VoidingReceipt voidRange(VoidingRequest request) throws VoidingException;
}
