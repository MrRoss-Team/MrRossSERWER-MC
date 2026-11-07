package pl.easytp.oneblock;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class IslandStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final int SPACING = 1024;
	static final int PER_ROW = 64;

	public enum Result {
		OK,
		ALREADY_HAS,
		NONE,
		NOT_OWNER,
		SELF,
		TARGET_HAS,
		NOT_MEMBER,
		NOT_INVITED,
		FULL,
		OWNER_CANNOT_LEAVE
	}

	public record Position(int x, int z) {
	}

	public static final class Island {
		public final int index;
		public UUID owner;
		public final Map<UUID, String> members = new LinkedHashMap<>();
		public long blocks;
		public long treasury;
		public int sizeLevel;
		public int memberLevel;
		public String theme = "";

		Island(int index, UUID owner) {
			this.index = index;
			this.owner = owner;
		}

		public int level() {
			return levelOf(blocks);
		}

		public String ownerName() {
			return members.getOrDefault(owner, "?");
		}
	}

	private record Invite(int index, long expiresAt) {
	}

	private final Path file;
	private final LongSupplier clock;
	private final Map<Integer, Island> islands = new LinkedHashMap<>();
	private final Map<UUID, Island> byMember = new HashMap<>();
	private final Map<UUID, Invite> invites = new HashMap<>();
	private int nextIndex;

	public IslandStore(Path file, LongSupplier clock) {
		this.file = file;
		this.clock = clock;
	}

	public static int blocksForLevel(int level) {
		return 10 + 2 * level;
	}

	public static int levelOf(long blocks) {
		int level = 0;
		long left = blocks;
		while (left >= blocksForLevel(level)) {
			left -= blocksForLevel(level);
			level++;
		}
		return level;
	}

	public static long progressOf(long blocks) {
		int level = 0;
		long left = blocks;
		while (left >= blocksForLevel(level)) {
			left -= blocksForLevel(level);
			level++;
		}
		return left;
	}

	public static Position center(int index) {
		return new Position((index % PER_ROW) * SPACING, (index / PER_ROW) * SPACING);
	}

	public static int halfSize(int sizeLevel) {
		return 25 + sizeLevel * 25;
	}

	public static int memberLimit(int memberLevel) {
		return 5 + memberLevel * 5;
	}

	public static boolean inside(Island island, int x, int z) {
		Position center = center(island.index);
		int half = halfSize(island.sizeLevel);
		return Math.abs(x - center.x()) <= half && Math.abs(z - center.z()) <= half;
	}

	public Optional<Island> islandAt(int x, int z) {
		int column = Math.floorDiv(x + SPACING / 2, SPACING);
		int row = Math.floorDiv(z + SPACING / 2, SPACING);
		if (column < 0 || column >= PER_ROW || row < 0) {
			return Optional.empty();
		}
		return Optional.ofNullable(islands.get(row * PER_ROW + column));
	}

	public Optional<Island> islandOf(UUID id) {
		return Optional.ofNullable(byMember.get(id));
	}

	public List<Island> all() {
		return List.copyOf(islands.values());
	}

	public List<Island> top() {
		List<Island> result = new ArrayList<>(islands.values());
		result.sort(Comparator.<Island>comparingLong(island -> island.blocks).reversed().thenComparingInt(island -> island.index));
		return result;
	}

	public Island create(UUID owner, String name) {
		Island island = new Island(nextIndex++, owner);
		island.members.put(owner, name);
		islands.put(island.index, island);
		byMember.put(owner, island);
		invites.remove(owner);
		persist();
		return island;
	}

	public Result invite(UUID actor, UUID target, long ttlMillis) {
		Island island = byMember.get(actor);
		if (island == null) {
			return Result.NONE;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.containsKey(target)) {
			return Result.TARGET_HAS;
		}
		invites.put(target, new Invite(island.index, clock.getAsLong() + ttlMillis));
		return Result.OK;
	}

	public Result join(UUID id, String name) {
		if (byMember.containsKey(id)) {
			return Result.ALREADY_HAS;
		}
		Invite invite = invites.get(id);
		if (invite == null || invite.expiresAt() <= clock.getAsLong() || !islands.containsKey(invite.index())) {
			return Result.NOT_INVITED;
		}
		Island island = islands.get(invite.index());
		if (island.members.size() >= memberLimit(island.memberLevel)) {
			return Result.FULL;
		}
		island.members.put(id, name);
		byMember.put(id, island);
		invites.remove(id);
		persist();
		return Result.OK;
	}

	public Result kick(UUID actor, UUID target) {
		Island island = byMember.get(actor);
		if (island == null) {
			return Result.NONE;
		}
		if (!actor.equals(island.owner)) {
			return Result.NOT_OWNER;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.get(target) != island) {
			return Result.NOT_MEMBER;
		}
		island.members.remove(target);
		byMember.remove(target);
		persist();
		return Result.OK;
	}

	public Result leave(UUID id) {
		Island island = byMember.get(id);
		if (island == null) {
			return Result.NONE;
		}
		if (id.equals(island.owner)) {
			return Result.OWNER_CANNOT_LEAVE;
		}
		island.members.remove(id);
		byMember.remove(id);
		persist();
		return Result.OK;
	}

	public Result delete(UUID actor) {
		Island island = byMember.get(actor);
		if (island == null) {
			return Result.NONE;
		}
		if (!actor.equals(island.owner)) {
			return Result.NOT_OWNER;
		}
		islands.remove(island.index);
		island.members.keySet().forEach(byMember::remove);
		persist();
		return Result.OK;
	}

	public boolean deposit(Island island, long amount) {
		if (amount <= 0) {
			return false;
		}
		island.treasury += amount;
		persist();
		return true;
	}

	public boolean spend(Island island, long amount) {
		if (amount < 0 || island.treasury < amount) {
			return false;
		}
		island.treasury -= amount;
		persist();
		return true;
	}

	public void touch() {
		persist();
	}

	public Optional<Path> load() throws IOException {
		islands.clear();
		byMember.clear();
		invites.clear();
		nextIndex = 0;
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("nextIndex")) {
				nextIndex = root.get("nextIndex").getAsInt();
			}
			if (root.has("islands") && root.get("islands").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("islands")) {
					readIsland(element);
				}
			}
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			islands.clear();
			byMember.clear();
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {}", file, backup, e);
			return Optional.of(backup);
		}
	}

	private void readIsland(JsonElement element) {
		try {
			JsonObject o = element.getAsJsonObject();
			Island island = new Island(o.get("index").getAsInt(), UUID.fromString(o.get("owner").getAsString()));
			for (Map.Entry<String, JsonElement> member : o.getAsJsonObject("members").entrySet()) {
				island.members.put(UUID.fromString(member.getKey()), member.getValue().getAsString());
			}
			if (!island.members.containsKey(island.owner)) {
				throw new IllegalStateException("wlasciciel spoza wyspy");
			}
			island.blocks = Math.max(0, o.get("blocks").getAsLong());
			island.treasury = Math.max(0, o.get("treasury").getAsLong());
			island.sizeLevel = Math.max(0, o.get("sizeLevel").getAsInt());
			island.memberLevel = Math.max(0, o.get("memberLevel").getAsInt());
			island.theme = o.has("theme") ? o.get("theme").getAsString() : "";
			islands.put(island.index, island);
			island.members.keySet().forEach(id -> byMember.put(id, island));
			nextIndex = Math.max(nextIndex, island.index + 1);
		} catch (RuntimeException e) {
			LOGGER.warn("EasyTP: pomijam uszkodzony wpis wyspy: {}", element);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("nextIndex", nextIndex);
		JsonArray array = new JsonArray();
		for (Island island : islands.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("index", island.index);
			o.addProperty("owner", island.owner.toString());
			JsonObject members = new JsonObject();
			island.members.forEach((id, name) -> members.addProperty(id.toString(), name));
			o.add("members", members);
			o.addProperty("blocks", island.blocks);
			o.addProperty("treasury", island.treasury);
			o.addProperty("sizeLevel", island.sizeLevel);
			o.addProperty("memberLevel", island.memberLevel);
			o.addProperty("theme", island.theme);
			array.add(o);
		}
		root.add("islands", array);
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
}
