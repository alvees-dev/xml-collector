package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public interface IssuedNumberRepository {

	void initialize() throws IOException;

	void saveAll(Collection<IssuedNumber> numbers) throws IOException;

	Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) throws IOException;
}
