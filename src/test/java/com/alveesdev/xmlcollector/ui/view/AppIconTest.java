package com.alveesdev.xmlcollector.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class AppIconTest {

	// Scaling down leaves a trace of the rounded edge in the corner of the smallest icons.
	private static final int ALMOST_INVISIBLE = 16;

	@Test
	void everyIconExistsAndIsASquareWithTransparentCorners() throws IOException {
		for (String resource : AppIcon.RESOURCES) {
			BufferedImage image = ImageIO.read(AppIcon.class.getResource(resource));

			assertNotNull(image, resource);
			assertEquals(image.getWidth(), image.getHeight(), resource);
			assertTrue(image.getColorModel().hasAlpha(), resource);
			assertTrue(image.getRGB(0, 0) >>> 24 < ALMOST_INVISIBLE, resource);
		}
	}
}
