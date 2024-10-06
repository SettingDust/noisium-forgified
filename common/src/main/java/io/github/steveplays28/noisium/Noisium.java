package io.github.steveplays28.noisium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Noisium {
	public static final String MOD_ID = "noisium_legacy";
	public static final String MOD_NAME = "Noisium Legacy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static void initialize() {
		LOGGER.info("Loading {}.", MOD_NAME);
	}
}
