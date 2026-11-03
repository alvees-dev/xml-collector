package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface ArchivedDocuments {

	Set<String> cnpjsIn(Path directory) throws IOException;
}
