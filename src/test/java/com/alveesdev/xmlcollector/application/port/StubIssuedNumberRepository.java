package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.util.Optional;

import com.alveesdev.xmlcollector.domain.model.IssuedNumber;

public interface StubIssuedNumberRepository extends IssuedNumberRepository {

	@Override
	default Optional<IssuedNumber> lastBefore(String cnpj, String modelCode, int series, int number) throws IOException {
		return Optional.empty();
	}
}
