package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.util.Collection;

import com.alveesdev.xmlcollector.domain.model.IssuedNumber;

public interface IssuedNumberRepository {

	void initialize() throws IOException;

	void saveAll(Collection<IssuedNumber> numbers) throws IOException;
}
