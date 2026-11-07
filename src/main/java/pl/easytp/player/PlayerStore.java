package pl.easytp.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;

public final class PlayerStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;
	private static final long MAX_MONEY = 1_000_000_000_000_000L;

	public record Entry(UUID id, PlayerData data) {
	}

	private final Path file;
	private final Map<UUID, PlayerData> players = new HashMap<>();
	private boolean dirty;

	public PlayerStore(Path file) {
		this.file = file;
	}

	public PlayerData get(UUID id) {
		return players.computeIfAbsent(id, key -> new PlayerData());
	}

	public PlayerData touch(UUID id, String name) {
		PlayerData data = get(id);
		if (!name.equals(data.name)) {
			data.name = name;
			dirty = true;
		}
		return data;
	}

	public void markDirty() {
		dirty = true;
	}

	public boolean isDirty() {
		return dirty;
	}

	public Optional<Entry> findByName(String name) {
		for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
			if (entry.getValue().name.equalsIgnoreCase(name)) {
				return Optional.of(new Entry(entry.getKey(), entry.getValue()));
			}
		}
		return Optional.empty();
	}

	public List<Entry> top(ToLongFunction<PlayerData> value, int limit) {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
			if (!entry.getValue().name.isEmpty() && value.applyAsLong(entry.getValue()) > 0) {
				entries.add(new Entry(entry.getKey(), entry.getValue()));
			}
		}
		entries.sort(Comparator.<Entry>comparingLong(e -> value.applyAsLong(e.data())).reversed()
			.thenComparing(e -> e.data().name.toLowerCase(Locale.ROOT)));
		return entries.size() > limit ? List.copyOf(entries.subList(0, limit)) : List.copyOf(entries);
	}

	public long deposit(UUID id, long amount) {
		PlayerData data = get(id);
		long added = Math.min(Math.max(0, amount), MAX_MONEY - data.money);
		data.money += added;
		data.earned += added;
		dirty = true;
		return data.money;
	}

	public boolean withdraw(UUID id, long amount) {
		PlayerData data = get(id);
		if (amount < 0 || data.money < amount) {
			return false;
		}
		data.money -= amount;
		data.spent += amount;
		dirty = true;
		return true;
	}

	public boolean transfer(UUID from, UUID to, long amount) {
		if (amount <= 0 || from.equals(to) || get(to).money + amount > MAX_MONEY || !withdraw(from, amount)) {
			return false;
		}
		deposit(to, amount);
		return true;
	}

	public long setMoney(UUID id, long amount) {
		PlayerData data = get(id);
		data.money = Math.max(0, Math.min(MAX_MONEY, amount));
		dirty = true;
		return data.money;
	}

	public Optional<Path> load() throws IOException {
		players.clear();
		dirty = false;
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("players") && root.get("players").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("players").entrySet()) {
					try {
						PlayerData data = GSON.fromJson(entry.getValue(), PlayerData.class);
						if (data != null) {
							if (data.name == null) {
								data.name = "";
							}
							data.money = Math.max(0, Math.min(MAX_MONEY, data.money));
							players.put(UUID.fromString(entry.getKey()), data);
						}
					} catch (RuntimeException e) {
						LOGGER.warn("EasyTP: pomijam uszkodzony wpis gracza {}", entry.getKey());
					}
				}
			}
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			players.clear();
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {}", file, backup, e);
			return Optional.of(backup);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		JsonObject list = new JsonObject();
		for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
			list.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
		}
		root.add("players", list);
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
		dirty = false;
	}

	public void flush() {
		if (!dirty) {
			return;
		}
		try {
			save();
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się zapisać pliku {}", file, e);
		}
	}
}
