package pl.easytp.session;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {
	@TempDir
	Path dir;

	@Test
	void saveLoadDelete() throws IOException {
		StateFiles files = new StateFiles(dir.resolve("a/b"));
		UUID id = UUID.randomUUID();
		assertFalse(files.exists(id));
		assertTrue(files.load(id).isEmpty());
		JsonObject state = new JsonObject();
		state.addProperty("health", 7);
		files.save(id, state);
		assertTrue(files.exists(id));
		assertEquals(7, files.load(id).orElseThrow().get("health").getAsInt());
		files.delete(id);
		assertFalse(files.exists(id));
		files.delete(id);
	}

	@Test
	void brokenFileIsNotLoadedButStaysOnDisk() throws IOException {
		StateFiles files = new StateFiles(dir);
		UUID id = UUID.randomUUID();
		Files.writeString(dir.resolve(id + ".json"), "{zle", StandardCharsets.UTF_8);
		assertTrue(files.load(id).isEmpty());
		assertTrue(files.exists(id));
	}

	@Test
	void commandRootIsNormalized() {
		assertEquals("ff", CommandGuard.rootOf("ff"));
		assertEquals("ff", CommandGuard.rootOf("/FF"));
		assertEquals("msg", CommandGuard.rootOf("msg Ala czesc"));
		assertEquals("tp", CommandGuard.rootOf("minecraft:tp Ala"));
		assertEquals("", CommandGuard.rootOf(""));
	}
}
