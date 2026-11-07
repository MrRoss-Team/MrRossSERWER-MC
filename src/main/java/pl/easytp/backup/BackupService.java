package pl.easytp.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupService {
	static final String SESSION_LOCK = "session.lock";
	static final long SPACE_MARGIN = 256L * 1024 * 1024;

	private static final Set<String> IGNORED_FOR_CHANGES = Set.of(SESSION_LOCK, "level.dat", "level.dat_old", "chunks.dat", "raids.dat", "raids_end.dat");

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String TEMP_SUFFIX = ".tmp";

	private BackupService() {
	}

	public record Result(Path file, long bytes, int files, long millis) {
	}

	public static final class CancelledException extends IOException {
		public CancelledException() {
			super("kopia zapasowa przerwana");
		}
	}

	public static String fileName(String worldName, LocalDateTime time) {
		return worldName + "-" + STAMP.format(time) + ".zip";
	}

	public static Path uniqueTarget(Path dir, String worldName, LocalDateTime time) {
		Path candidate = dir.resolve(fileName(worldName, time));
		String base = worldName + "-" + STAMP.format(time);
		for (int n = 2; Files.exists(candidate); n++) {
			candidate = dir.resolve(base + "_" + n + ".zip");
		}
		return candidate;
	}

	private static Pattern namePattern(String worldName) {
		return Pattern.compile("^" + Pattern.quote(worldName) + "-\\d{8}-\\d{6}(_\\d+)?\\.zip$");
	}

	public static List<Path> listBackups(Path dir, String worldName) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		Pattern pattern = namePattern(worldName);
		try (Stream<Path> files = Files.list(dir)) {
			return files
				.filter(Files::isRegularFile)
				.filter(path -> pattern.matcher(path.getFileName().toString()).matches())
				.sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
				.toList();
		}
	}

	public static List<Path> prune(Path dir, String worldName, int keep) throws IOException {
		List<Path> all = listBackups(dir, worldName);
		List<Path> deleted = new ArrayList<>();
		for (int i = Math.max(keep, 1); i < all.size(); i++) {
			Files.deleteIfExists(all.get(i));
			deleted.add(all.get(i));
		}
		return deleted;
	}

	public static void cleanupTemp(Path dir) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".zip" + TEMP_SUFFIX)).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (IOException ignored) {
		}
	}

	public static long newestModified(Path worldDir) throws IOException {
		long[] newest = {0};
		Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!IGNORED_FOR_CHANGES.contains(file.getFileName().toString())) {
					newest[0] = Math.max(newest[0], attrs.lastModifiedTime().toMillis());
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
		return newest[0];
	}

	public static boolean needsBackup(Path worldDir, Path newestBackup) throws IOException {
		if (newestBackup == null) {
			return true;
		}
		return newestModified(worldDir) > Files.getLastModifiedTime(newestBackup).toMillis();
	}

	public static long totalSize(Path worldDir) throws IOException {
		long[] total = {0};
		Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!file.getFileName().toString().equals(SESSION_LOCK)) {
					total[0] += attrs.size();
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
		return total[0];
	}

	public static boolean hasEnoughSpace(long usableBytes, long worldBytes) {
		return usableBytes >= worldBytes + SPACE_MARGIN;
	}

	public static long requiredSpace(long worldBytes) {
		return worldBytes + SPACE_MARGIN;
	}

	public static Result zipWorld(Path worldDir, Path target, BooleanSupplier cancelled) throws IOException {
		long started = System.nanoTime();
		String root = worldDir.getFileName().toString();
		Path tmp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
		Files.createDirectories(target.toAbsolutePath().getParent());
		int[] count = {0};
		try {
			try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
				Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), 1 << 16))) {
				zip.setLevel(Deflater.BEST_SPEED);
				Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (cancelled.getAsBoolean()) {
							throw new CancelledException();
						}
						if (file.getFileName().toString().equals(SESSION_LOCK)) {
							return FileVisitResult.CONTINUE;
						}
						InputStream in;
						try {
							in = Files.newInputStream(file);
						} catch (NoSuchFileException vanished) {
							return FileVisitResult.CONTINUE;
						}
						try (in) {
							ZipEntry entry = new ZipEntry(root + "/" + worldDir.relativize(file).toString().replace('\\', '/'));
							entry.setTime(attrs.lastModifiedTime().toMillis());
							zip.putNextEntry(entry);
							in.transferTo(zip);
							zip.closeEntry();
							count[0]++;
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						if (exc instanceof NoSuchFileException) {
							return FileVisitResult.CONTINUE;
						}
						throw exc;
					}
				});
			}

			try (ZipFile check = new ZipFile(tmp.toFile())) {
				if (check.size() != count[0]) {
					throw new IOException("kontrola ZIP nie powiodła się: " + check.size() + " wpisów zamiast " + count[0]);
				}
			}
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
		return new Result(target, Files.size(target), count[0], (System.nanoTime() - started) / 1_000_000);
	}

	public static String formatSize(long bytes) {
		double value = bytes;
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unit = 0;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return unit == 0 ? bytes + " B" : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
