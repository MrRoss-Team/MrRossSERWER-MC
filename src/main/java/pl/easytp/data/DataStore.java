package pl.easytp.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.easytp.util.Names;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class DataStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;

	private final Path file;
	private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
	private Location spawn;

	public DataStore(Path file) {
		this.file = file;
	}

	public Optional<Path> load() throws IOException {
		homes.clear();
		spawn = null;
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			readHomes(root);
			readSpawn(root);
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			homes.clear();
			spawn = null;
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {} i zaczynam od pustych danych", file, backup, e);
			return Optional.of(backup);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		if (spawn != null) {
			root.add("spawn", writeLocation(spawn));
		}
		JsonObject allHomes = new JsonObject();
		for (Map.Entry<UUID, Map<String, Location>> player : new TreeMap<>(homes).entrySet()) {
			if (player.getValue().isEmpty()) {
				continue;
			}
			JsonObject playerHomes = new JsonObject();
			for (Map.Entry<String, Location> home : player.getValue().entrySet()) {
				playerHomes.add(home.getKey(), writeLocation(home.getValue()));
			}
			allHomes.add(player.getKey().toString(), playerHomes);
		}
		root.add("homes", allHomes);

		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public Map<String, Location> homesOf(UUID player) {
		Map<String, Location> own = homes.get(player);
		return own == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(own));
	}

	public Optional<Location> getHome(UUID player, String name) {
		Map<String, Location> own = homes.get(player);
		return own == null ? Optional.empty() : Optional.ofNullable(own.get(Names.normalize(name)));
	}

	public int homeCount(UUID player) {
		Map<String, Location> own = homes.get(player);
		return own == null ? 0 : own.size();
	}

	public void setHome(UUID player, String name, Location location) {
		homes.computeIfAbsent(player, id -> new TreeMap<>()).put(Names.normalize(name), location);
		persist();
	}

	public boolean removeHome(UUID player, String name) {
		Map<String, Location> own = homes.get(player);
		if (own == null || own.remove(Names.normalize(name)) == null) {
			return false;
		}
		if (own.isEmpty()) {
			homes.remove(player);
		}
		persist();
		return true;
	}

	public Optional<Location> spawn() {
		return Optional.ofNullable(spawn);
	}

	public void setSpawn(Location location) {
		this.spawn = location;
		persist();
	}

	private void persist() {
		try {
			save();
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się zapisać pliku {}", file, e);
		}
	}

	private void readHomes(JsonObject root) {
		if (!root.has("homes") || !root.get("homes").isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> player : root.getAsJsonObject("homes").entrySet()) {
			UUID id;
			try {
				id = UUID.fromString(player.getKey());
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (!player.getValue().isJsonObject()) {
				continue;
			}
			for (Map.Entry<String, JsonElement> home : player.getValue().getAsJsonObject().entrySet()) {
				String name = Names.normalize(home.getKey());
				Location location = readLocation(home.getValue());
				if (location != null && Names.isValid(name)) {
					homes.computeIfAbsent(id, k -> new TreeMap<>()).put(name, location);
				}
			}
		}
	}

	private void readSpawn(JsonObject root) {
		if (root.has("spawn")) {
			spawn = readLocation(root.get("spawn"));
		}
	}

	private static Location readLocation(JsonElement element) {
		try {
			JsonObject o = element.getAsJsonObject();
			String dimension = o.get("dimension").getAsString();
			double x = o.get("x").getAsDouble();
			double y = o.get("y").getAsDouble();
			double z = o.get("z").getAsDouble();
			float yaw = o.has("yaw") ? o.get("yaw").getAsFloat() : 0f;
			float pitch = o.has("pitch") ? o.get("pitch").getAsFloat() : 0f;
			boolean finite = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& Float.isFinite(yaw) && Float.isFinite(pitch);
			if (dimension.isBlank() || !finite) {
				return null;
			}
			return new Location(dimension, x, y, z, yaw, pitch);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static JsonObject writeLocation(Location location) {
		JsonObject o = new JsonObject();
		o.addProperty("dimension", location.dimension());
		o.addProperty("x", location.x());
		o.addProperty("y", location.y());
		o.addProperty("z", location.z());
		o.addProperty("yaw", location.yaw());
		o.addProperty("pitch", location.pitch());
		return o;
	}
}
