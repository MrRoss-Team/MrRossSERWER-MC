package pl.easytp.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStoreTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final Location BASE = new Location("minecraft:overworld", 10.5, 64, -3.25, 90f, 12.5f);
	private static final Location NETHER = new Location("minecraft:the_nether", 1, 2, 3, 0f, 0f);

	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("world").resolve("easytp").resolve("data.json");
	}

	@Test
	void roundTripKeepsHomesAndSpawn() throws IOException {
		DataStore store = new DataStore(file());
		store.setHome(ALICE, "base", BASE);
		store.setHome(ALICE, "nether", NETHER);
		store.setHome(BOB, "home", BASE);
		store.setSpawn(NETHER);

		DataStore reloaded = new DataStore(file());
		assertTrue(reloaded.load().isEmpty());
		assertEquals(Optional.of(BASE), reloaded.getHome(ALICE, "base"));
		assertEquals(Optional.of(NETHER), reloaded.getHome(ALICE, "nether"));
		assertEquals(Optional.of(BASE), reloaded.getHome(BOB, "home"));
		assertEquals(List.of("base", "nether"), List.copyOf(reloaded.homesOf(ALICE).keySet()));
		assertEquals(2, reloaded.homeCount(ALICE));
		assertEquals(Optional.of(NETHER), reloaded.spawn());
	}

	@Test
	void homeNamesAreCaseInsensitive() {
		DataStore store = new DataStore(file());
		store.setHome(ALICE, "HoMe", BASE);
		assertEquals(Optional.of(BASE), store.getHome(ALICE, "home"));
		assertEquals(Optional.of(BASE), store.getHome(ALICE, "HOME"));
		store.setHome(ALICE, "HOME", NETHER);
		assertEquals(1, store.homeCount(ALICE));
		assertEquals(Optional.of(NETHER), store.getHome(ALICE, "home"));
	}

	@Test
	void removeHomeReportsWhetherAnythingWasRemoved() throws IOException {
		DataStore store = new DataStore(file());
		store.setHome(ALICE, "a", BASE);
		assertFalse(store.removeHome(ALICE, "b"));
		assertFalse(store.removeHome(BOB, "a"));
		assertTrue(store.removeHome(ALICE, "A"));
		assertEquals(0, store.homeCount(ALICE));

		DataStore reloaded = new DataStore(file());
		reloaded.load();
		assertTrue(reloaded.getHome(ALICE, "a").isEmpty());
	}

	@Test
	void homesOfReturnsSortedReadOnlyCopy() {
		DataStore store = new DataStore(file());
		store.setHome(ALICE, "zeta", BASE);
		store.setHome(ALICE, "alfa", NETHER);
		Map<String, Location> homes = store.homesOf(ALICE);
		assertEquals(List.of("alfa", "zeta"), List.copyOf(homes.keySet()));
		assertThrows(UnsupportedOperationException.class, () -> homes.put("x", BASE));
		assertTrue(store.homesOf(BOB).isEmpty());
	}

	@Test
	void corruptFileIsMovedAsideAndStoreKeepsWorking() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ to nie jest json");

		DataStore store = new DataStore(file());
		Optional<Path> backup = store.load();
		assertTrue(backup.isPresent());
		assertTrue(Files.exists(backup.get()));
		assertFalse(Files.exists(file()));
		assertEquals(0, store.homeCount(ALICE));

		store.setHome(ALICE, "home", BASE);
		assertTrue(Files.exists(file()));
	}

	@Test
	void invalidEntriesAreSkippedButValidOnesSurvive() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
			{
			  "version": 1,
			  "homes": {
			    "not-a-uuid": { "x": { "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3 } },
			    "00000000-0000-0000-0000-000000000001": {
			      "good": { "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3, "yaw": 4, "pitch": 5 },
			      "no-y": { "dimension": "minecraft:overworld", "x": 1, "z": 3 },
			      "bad name!": { "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3 },
			      "text": "oops"
			    }
			  },
			  "spawn": { "dimension": "", "x": 0, "y": 0, "z": 0 }
			}
			""");

		DataStore store = new DataStore(file());
		assertTrue(store.load().isEmpty());
		assertEquals(List.of("good"), List.copyOf(store.homesOf(ALICE).keySet()));
		assertEquals(new Location("minecraft:overworld", 1, 2, 3, 4f, 5f), store.homesOf(ALICE).get("good"));
		assertTrue(store.spawn().isEmpty());
	}
}
