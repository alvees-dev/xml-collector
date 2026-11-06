package com.alveesdev.xmlcollector.infra.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class SystemTrustTest {

	@Test
	void trustsTheRootsThatJavaBrings() throws Exception {
		assertTrue(aliases().stream().anyMatch(alias -> alias.startsWith("java-")));
	}

	@Test
	void onWindowsAlsoTrustsTheRootsOfWindows() throws Exception {
		assumeTrue(System.getProperty("os.name").startsWith("Windows"));

		assertTrue(aliases().stream().anyMatch(alias -> alias.startsWith("windows-")));
	}

	private static List<String> aliases() throws Exception {
		KeyStore roots = SystemTrust.roots();
		return Collections.list(roots.aliases());
	}
}
