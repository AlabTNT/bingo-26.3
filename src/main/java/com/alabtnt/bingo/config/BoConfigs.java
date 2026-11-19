package com.alabtnt.bingo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoConfigs {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final Logger LOGGER = LoggerFactory.getLogger("bingo_ourselves");
	private static final String DIR = "bingo-ourselves";

	private static ServerConfig server;
	private static ClientConfig client;

	private BoConfigs() {
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static ServerConfig server() {
		if (server == null) {
			server = load(configDir().resolve(DIR).resolve("server.json"), ServerConfig.class, new ServerConfig());
			saveServer();
		}
		return server;
	}

	public static ClientConfig client() {
		if (client == null) {
			client = load(configDir().resolve(DIR).resolve("client.json"), ClientConfig.class, new ClientConfig());
			saveClient();
		}
		return client;
	}

	public static void saveServer() {
		write(configDir().resolve(DIR).resolve("server.json"), server);
	}

	public static void saveClient() {
		write(configDir().resolve(DIR).resolve("client.json"), client);
	}

	public static ServerConfig reloadServer() {
		server = load(configDir().resolve(DIR).resolve("server.json"), ServerConfig.class, new ServerConfig());
		return server;
	}

	public static void setServer(ServerConfig config) {
		server = config;
		saveServer();
	}

	private static <T> T load(Path path, Class<T> type, T fallback) {
		try {
			if (!Files.exists(path)) return fallback;
			return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
		} catch (Exception e) {
			LOGGER.error("Could not read {}", path, e);
			return fallback;
		}
	}

	public static void write(Path path, Object value) {
		if (value == null) return;
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write {}", path, e);
		}
	}
}
