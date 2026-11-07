package pl.easytp.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class ModerationStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;

	static final int DEFAULT_MAX_HISTORY = 5000;
	static final int MAX_WARNINGS_PER_PLAYER = 100;

	public enum Type {
		BAN,
		UNBAN,
		MUTE,
		UNMUTE,
		WARN,
		CLEAR_WARNINGS
	}

	public record Mute(UUID id, String name, String by, String reason, long createdAt, long expiresAt) {
		public boolean isPermanent() {
			return expiresAt == 0;
		}

		public boolean isActive(long now) {
			return expiresAt == 0 || now < expiresAt;
		}
	}

	public record Warning(long time, String by, String reason) {
	}

	public record HistoryEntry(long time, Type type, UUID id, String name, String by, String reason, long durationMillis) {
	}

	private final Path file;
	private final LongSupplier clock;
	private final int maxHistory;
	private final Map<UUID, Mute> mutes = new LinkedHashMap<>();
	private final Map<UUID, List<Warning>> warnings = new HashMap<>();
	private final List<HistoryEntry> history = new ArrayList<>();

	public ModerationStore(Path file, LongSupplier clock) {
		this(file, clock, DEFAULT_MAX_HISTORY);
	}

	ModerationStore(Path file, LongSupplier clock, int maxHistory) {
		this.file = file;
		this.clock = clock;
		this.maxHistory = maxHistory;
	}

	public Mute mute(UUID id, String name, String by, String reason, long durationMillis) {
		long now = clock.getAsLong();
		Mute mute = new Mute(id, name, by, reason, now, durationMillis > 0 ? now + durationMillis : 0);
		mutes.put(id, mute);
		persist();
		return mute;
	}

	public boolean unmute(UUID id) {
		Mute removed = mutes.remove(id);
		if (removed == null) {
			return false;
		}
		persist();
		return removed.isActive(clock.getAsLong());
	}

	public Optional<Mute> activeMute(UUID id) {
		Mute mute = mutes.get(id);
		if (mute == null) {
			return Optional.empty();
		}
		if (!mute.isActive(clock.getAsLong())) {
			mutes.remove(id);
			persist();
			return Optional.empty();
		}
		return Optional.of(mute);
	}

	public List<Mute> activeMutes() {
		purgeExpiredMutes();
		List<Mute> result = new ArrayList<>(mutes.values());
		result.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
		return result;
	}

	public List<Mute> purgeExpiredMutes() {
		long now = clock.getAsLong();
		List<Mute> expired = new ArrayList<>();
		Iterator<Mute> it = mutes.values().iterator();
		while (it.hasNext()) {
			Mute mute = it.next();
			if (!mute.isActive(now)) {
				expired.add(mute);
				it.remove();
			}
		}
		if (!expired.isEmpty()) {
			persist();
		}
		return expired;
	}

	public int warn(UUID id, String by, String reason) {
		List<Warning> list = warnings.computeIfAbsent(id, k -> new ArrayList<>());
		list.add(new Warning(clock.getAsLong(), by, reason));
		while (list.size() > MAX_WARNINGS_PER_PLAYER) {
			list.remove(0);
		}
		persist();
		return list.size();
	}

	public List<Warning> warningsOf(UUID id) {
		List<Warning> list = warnings.get(id);
		return list == null ? List.of() : List.copyOf(list);
	}

	public int clearWarnings(UUID id) {
		List<Warning> removed = warnings.remove(id);
		if (removed == null) {
			return 0;
		}
		persist();
		return removed.size();
	}

	public void log(Type type, UUID id, String name, String by, String reason, long durationMillis) {
		history.add(new HistoryEntry(clock.getAsLong(), type, id, name, by, reason, Math.max(0, durationMillis)));
		while (history.size() > maxHistory) {
			history.remove(0);
		}
		persist();
	}

	public List<HistoryEntry> historyOf(UUID id) {
		List<HistoryEntry> result = new ArrayList<>();
		for (HistoryEntry entry : history) {
			if (entry.id().equals(id)) {
				result.add(entry);
			}
		}
		return result;
	}

	public Optional<Path> load() throws IOException {
		mutes.clear();
		warnings.clear();
		history.clear();
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			readMutes(root);
			readWarnings(root);
			readHistory(root);
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			mutes.clear();
			warnings.clear();
			history.clear();
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {} i zaczynam od pustej moderacji", file, backup, e);
			return Optional.of(backup);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);

		JsonArray muteArray = new JsonArray();
		for (Mute mute : mutes.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("uuid", mute.id().toString());
			o.addProperty("name", mute.name());
			o.addProperty("by", mute.by());
			o.addProperty("reason", mute.reason());
			o.addProperty("createdAt", mute.createdAt());
			o.addProperty("expiresAt", mute.expiresAt());
			muteArray.add(o);
		}
		root.add("mutes", muteArray);

		JsonObject warningObject = new JsonObject();
		for (Map.Entry<UUID, List<Warning>> player : warnings.entrySet()) {
			if (player.getValue().isEmpty()) {
				continue;
			}
			JsonArray list = new JsonArray();
			for (Warning warning : player.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("time", warning.time());
				o.addProperty("by", warning.by());
				o.addProperty("reason", warning.reason());
				list.add(o);
			}
			warningObject.add(player.getKey().toString(), list);
		}
		root.add("warnings", warningObject);

		JsonArray historyArray = new JsonArray();
		for (HistoryEntry entry : history) {
			JsonObject o = new JsonObject();
			o.addProperty("time", entry.time());
			o.addProperty("type", entry.type().name());
			o.addProperty("uuid", entry.id().toString());
			o.addProperty("name", entry.name());
			o.addProperty("by", entry.by());
			o.addProperty("reason", entry.reason());
			o.addProperty("durationMillis", entry.durationMillis());
			historyArray.add(o);
		}
		root.add("history", historyArray);

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

	private void persist() {
		try {
			save();
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się zapisać pliku {}", file, e);
		}
	}

	private void readMutes(JsonObject root) {
		if (!root.has("mutes") || !root.get("mutes").isJsonArray()) {
			return;
		}
		for (JsonElement element : root.getAsJsonArray("mutes")) {
			try {
				JsonObject o = element.getAsJsonObject();
				UUID id = UUID.fromString(o.get("uuid").getAsString());
				mutes.put(id, new Mute(id, text(o, "name"), text(o, "by"), text(o, "reason"),
					number(o, "createdAt"), Math.max(0, number(o, "expiresAt"))));
			} catch (RuntimeException e) {
			}
		}
	}

	private void readWarnings(JsonObject root) {
		if (!root.has("warnings") || !root.get("warnings").isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> player : root.getAsJsonObject("warnings").entrySet()) {
			UUID id;
			try {
				id = UUID.fromString(player.getKey());
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (!player.getValue().isJsonArray()) {
				continue;
			}
			List<Warning> list = new ArrayList<>();
			for (JsonElement element : player.getValue().getAsJsonArray()) {
				try {
					JsonObject o = element.getAsJsonObject();
					list.add(new Warning(number(o, "time"), text(o, "by"), text(o, "reason")));
				} catch (RuntimeException e) {
				}
			}
			if (!list.isEmpty()) {
				warnings.put(id, list);
			}
		}
	}

	private void readHistory(JsonObject root) {
		if (!root.has("history") || !root.get("history").isJsonArray()) {
			return;
		}
		for (JsonElement element : root.getAsJsonArray("history")) {
			try {
				JsonObject o = element.getAsJsonObject();
				history.add(new HistoryEntry(number(o, "time"), Type.valueOf(o.get("type").getAsString()),
					UUID.fromString(o.get("uuid").getAsString()), text(o, "name"), text(o, "by"), text(o, "reason"),
					Math.max(0, number(o, "durationMillis"))));
			} catch (RuntimeException e) {
			}
		}
		while (history.size() > maxHistory) {
			history.remove(0);
		}
	}

	private static String text(JsonObject o, String key) {
		JsonElement element = o.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static long number(JsonObject o, String key) {
		JsonElement element = o.get(key);
		return element == null || element.isJsonNull() ? 0 : element.getAsLong();
	}
}
