package pl.easytp.clan;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class ClanStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,5}");
	private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 _-]{3,24}");

	public enum Result {
		OK,
		BAD_TAG,
		BAD_NAME,
		TAG_TAKEN,
		NAME_TAKEN,
		ALREADY_IN_CLAN,
		NOT_IN_CLAN,
		NO_PERMISSION,
		TARGET_IN_CLAN,
		TARGET_NOT_MEMBER,
		SELF,
		UNKNOWN_CLAN,
		NOT_INVITED,
		FULL,
		LEADER_MUST_TRANSFER
	}

	public static final class Clan {
		public final String tag;
		public final String name;
		public UUID leader;
		public final long createdAt;
		public final Map<UUID, String> members = new LinkedHashMap<>();
		public final Set<UUID> deputies = new LinkedHashSet<>();

		Clan(String tag, String name, UUID leader, long createdAt) {
			this.tag = tag;
			this.name = name;
			this.leader = leader;
			this.createdAt = createdAt;
		}

		public boolean canManage(UUID id) {
			return id.equals(leader) || deputies.contains(id);
		}
	}

	private record Invite(String tag, long expiresAt) {
	}

	private final Path file;
	private final LongSupplier clock;
	private final Map<String, Clan> clans = new LinkedHashMap<>();
	private final Map<UUID, Clan> byMember = new HashMap<>();
	private final Map<UUID, List<Invite>> invites = new HashMap<>();

	public ClanStore(Path file, LongSupplier clock) {
		this.file = file;
		this.clock = clock;
	}

	public static String normalizeTag(String tag) {
		return tag.toUpperCase(Locale.ROOT);
	}

	public Optional<Clan> clanOf(UUID id) {
		return Optional.ofNullable(byMember.get(id));
	}

	public Optional<Clan> byTag(String tag) {
		return Optional.ofNullable(clans.get(normalizeTag(tag)));
	}

	public List<Clan> all() {
		List<Clan> result = new ArrayList<>(clans.values());
		result.sort(Comparator.comparing(clan -> clan.tag));
		return result;
	}

	public boolean sameClan(UUID a, UUID b) {
		Clan clan = byMember.get(a);
		return clan != null && clan == byMember.get(b);
	}

	public Result create(String tag, String name, UUID leader, String leaderName) {
		if (tag == null || !TAG.matcher(tag).matches()) {
			return Result.BAD_TAG;
		}
		String cleanName = name == null ? "" : name.trim();
		if (!NAME.matcher(cleanName).matches()) {
			return Result.BAD_NAME;
		}
		if (byMember.containsKey(leader)) {
			return Result.ALREADY_IN_CLAN;
		}
		String normalized = normalizeTag(tag);
		if (clans.containsKey(normalized)) {
			return Result.TAG_TAKEN;
		}
		for (Clan clan : clans.values()) {
			if (clan.name.equalsIgnoreCase(cleanName)) {
				return Result.NAME_TAKEN;
			}
		}
		Clan clan = new Clan(normalized, cleanName, leader, clock.getAsLong());
		clan.members.put(leader, leaderName);
		clans.put(normalized, clan);
		byMember.put(leader, clan);
		invites.remove(leader);
		persist();
		return Result.OK;
	}

	public Result invite(UUID actor, UUID target, long ttlMillis) {
		Clan clan = byMember.get(actor);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (!clan.canManage(actor)) {
			return Result.NO_PERMISSION;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.containsKey(target)) {
			return Result.TARGET_IN_CLAN;
		}
		List<Invite> list = invites.computeIfAbsent(target, key -> new ArrayList<>());
		list.removeIf(invite -> invite.tag().equals(clan.tag));
		list.add(new Invite(clan.tag, clock.getAsLong() + ttlMillis));
		return Result.OK;
	}

	public Result join(String tag, UUID id, String name, int maxMembers) {
		if (byMember.containsKey(id)) {
			return Result.ALREADY_IN_CLAN;
		}
		Clan clan = clans.get(normalizeTag(tag));
		if (clan == null) {
			return Result.UNKNOWN_CLAN;
		}
		long now = clock.getAsLong();
		List<Invite> list = invites.get(id);
		boolean invited = list != null && list.stream().anyMatch(invite -> invite.tag().equals(clan.tag) && invite.expiresAt() > now);
		if (!invited) {
			return Result.NOT_INVITED;
		}
		if (clan.members.size() >= maxMembers) {
			return Result.FULL;
		}
		clan.members.put(id, name);
		byMember.put(id, clan);
		invites.remove(id);
		persist();
		return Result.OK;
	}

	public Result kick(UUID actor, UUID target) {
		Clan clan = byMember.get(actor);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (!clan.canManage(actor)) {
			return Result.NO_PERMISSION;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.get(target) != clan) {
			return Result.TARGET_NOT_MEMBER;
		}
		if (target.equals(clan.leader) || (clan.deputies.contains(target) && !actor.equals(clan.leader))) {
			return Result.NO_PERMISSION;
		}
		removeMember(clan, target);
		persist();
		return Result.OK;
	}

	public Result leave(UUID id) {
		Clan clan = byMember.get(id);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (id.equals(clan.leader)) {
			return Result.LEADER_MUST_TRANSFER;
		}
		removeMember(clan, id);
		persist();
		return Result.OK;
	}

	public Result toggleDeputy(UUID actor, UUID target) {
		Clan clan = byMember.get(actor);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (!actor.equals(clan.leader)) {
			return Result.NO_PERMISSION;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.get(target) != clan) {
			return Result.TARGET_NOT_MEMBER;
		}
		if (!clan.deputies.remove(target)) {
			clan.deputies.add(target);
		}
		persist();
		return Result.OK;
	}

	public Result transferLeader(UUID actor, UUID target) {
		Clan clan = byMember.get(actor);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (!actor.equals(clan.leader)) {
			return Result.NO_PERMISSION;
		}
		if (actor.equals(target)) {
			return Result.SELF;
		}
		if (byMember.get(target) != clan) {
			return Result.TARGET_NOT_MEMBER;
		}
		clan.leader = target;
		clan.deputies.remove(target);
		clan.deputies.add(actor);
		persist();
		return Result.OK;
	}

	public Result disband(UUID actor) {
		Clan clan = byMember.get(actor);
		if (clan == null) {
			return Result.NOT_IN_CLAN;
		}
		if (!actor.equals(clan.leader)) {
			return Result.NO_PERMISSION;
		}
		clans.remove(clan.tag);
		clan.members.keySet().forEach(byMember::remove);
		for (List<Invite> list : invites.values()) {
			list.removeIf(invite -> invite.tag().equals(clan.tag));
		}
		persist();
		return Result.OK;
	}

	public boolean rename(UUID id, String name) {
		Clan clan = byMember.get(id);
		if (clan == null || name.equals(clan.members.get(id))) {
			return false;
		}
		clan.members.put(id, name);
		persist();
		return true;
	}

	private void removeMember(Clan clan, UUID id) {
		clan.members.remove(id);
		clan.deputies.remove(id);
		byMember.remove(id);
	}

	public Optional<Path> load() throws IOException {
		clans.clear();
		byMember.clear();
		invites.clear();
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("clans") && root.get("clans").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("clans")) {
					readClan(element);
				}
			}
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			clans.clear();
			byMember.clear();
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {}", file, backup, e);
			return Optional.of(backup);
		}
	}

	private void readClan(JsonElement element) {
		try {
			JsonObject o = element.getAsJsonObject();
			String tag = normalizeTag(o.get("tag").getAsString());
			Clan clan = new Clan(tag, o.get("name").getAsString(), UUID.fromString(o.get("leader").getAsString()), o.get("createdAt").getAsLong());
			for (Map.Entry<String, JsonElement> member : o.getAsJsonObject("members").entrySet()) {
				clan.members.put(UUID.fromString(member.getKey()), member.getValue().getAsString());
			}
			for (JsonElement deputy : o.getAsJsonArray("deputies")) {
				UUID id = UUID.fromString(deputy.getAsString());
				if (clan.members.containsKey(id) && !id.equals(clan.leader)) {
					clan.deputies.add(id);
				}
			}
			if (!clan.members.containsKey(clan.leader)) {
				throw new IllegalStateException("lider spoza klanu");
			}
			clans.put(tag, clan);
			clan.members.keySet().forEach(id -> byMember.put(id, clan));
		} catch (RuntimeException e) {
			LOGGER.warn("EasyTP: pomijam uszkodzony wpis klanu: {}", element);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray array = new JsonArray();
		for (Clan clan : clans.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("tag", clan.tag);
			o.addProperty("name", clan.name);
			o.addProperty("leader", clan.leader.toString());
			o.addProperty("createdAt", clan.createdAt);
			JsonObject members = new JsonObject();
			clan.members.forEach((id, name) -> members.addProperty(id.toString(), name));
			o.add("members", members);
			JsonArray deputies = new JsonArray();
			clan.deputies.forEach(id -> deputies.add(id.toString()));
			o.add("deputies", deputies);
			array.add(o);
		}
		root.add("clans", array);
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
