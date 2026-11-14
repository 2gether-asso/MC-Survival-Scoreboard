package fr._2gether_asso.survival_scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.RenderType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Owns the groups, their persistence and the assignment of scoreboards to players */
final class GroupManager {
	/** Slots a group scoreboard can show (the per-player sidebar.team.* slots are not needed here) */
	static final Map<String, DisplaySlot> SLOTS = Map.of(
			"list", DisplaySlot.PLAYER_LIST,
			"sidebar", DisplaySlot.SIDEBAR,
			"below_name", DisplaySlot.BELOW_NAME,
			"belowname", DisplaySlot.BELOW_NAME);

	private final SurvivalScoreboardPlugin plugin;
	private final File stateFile;
	private final Map<String, Group> groups = new LinkedHashMap<>();
	private final Map<String, Group> byWorld = new LinkedHashMap<>();
	/** State of groups that are no longer defined: kept so their data is not lost */
	private final Map<String, Map<?, ?>> orphanState = new LinkedHashMap<>();
	private boolean dirty;

	GroupManager(SurvivalScoreboardPlugin plugin) {
		this.plugin = plugin;
		this.stateFile = new File(plugin.getDataFolder(), "state.yml");
	}

	Map<String, Group> groups() {
		return groups;
	}

	Group group(String name) {
		Group group = groups.get(name);
		if (group == null) {
			throw new CommandFailure("Unknown group '" + name + "' (see /mvsb groups)");
		}
		return group;
	}

	Group groupOf(World world) {
		return byWorld.get(world.getName());
	}

	void markDirty() {
		dirty = true;
	}

	// ---- Loading ----

	/** (Re)reads the group definitions and the saved state, then applies them to the online players */
	void reload() {
		if (!groups.isEmpty()) {
			save();
		}
		plugin.reloadConfig();
		groups.clear();
		byWorld.clear();
		orphanState.clear();

		Map<String, Set<String>> definitions = new LinkedHashMap<>();
		if (plugin.getConfig().getBoolean("multiverse-inventories-groups", true)) {
			loadMultiverseInventoriesGroups(definitions);
		}
		ConfigurationSection extra = plugin.getConfig().getConfigurationSection("groups");
		if (extra != null) {
			for (String name : extra.getKeys(false)) {
				definitions.computeIfAbsent(name, k -> new LinkedHashSet<>()).addAll(extra.getStringList(name));
			}
		}
		for (Map.Entry<String, Set<String>> entry : definitions.entrySet()) {
			Group group = new Group(entry.getKey(), entry.getValue());
			groups.put(group.name, group);
			for (String world : group.worlds) {
				Group previous = byWorld.putIfAbsent(world, group);
				if (previous != null) {
					plugin.getLogger().warning("World " + world + " is in groups " + previous.name + " and "
							+ group.name + ": using " + previous.name);
				}
			}
		}
		loadState();
		plugin.getLogger().info("Loaded " + groups.size() + " group(s): " + String.join(", ", groups.keySet()));
		applyAll();
	}

	private void loadMultiverseInventoriesGroups(Map<String, Set<String>> definitions) {
		File file = new File(plugin.getDataFolder().getParentFile(), "Multiverse-Inventories/groups.yml");
		if (!file.isFile()) {
			return;
		}
		ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("groups");
		if (section == null) {
			return;
		}
		for (String name : section.getKeys(false)) {
			ConfigurationSection group = section.getConfigurationSection(name);
			if (group != null) {
				definitions.computeIfAbsent(name, k -> new LinkedHashSet<>()).addAll(group.getStringList("worlds"));
			}
		}
	}

	private void loadState() {
		if (!stateFile.isFile()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
		for (Map<?, ?> raw : yaml.getMapList("groups")) {
			String name = String.valueOf(raw.get("name"));
			Group group = groups.get(name);
			if (group == null) {
				orphanState.put(name, raw);
				continue;
			}
			try {
				restore(group, raw);
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, "Could not restore the state of group " + name, e);
			}
		}
	}

	private void restore(Group group, Map<?, ?> raw) {
		for (Object o : list(raw.get("objectives"))) {
			Map<?, ?> map = (Map<?, ?>) o;
			String name = String.valueOf(map.get("name"));
			CriteriaKind kind = CriteriaKind.parse(String.valueOf(map.get("criteria"))).orElse(null);
			if (kind == null) {
				plugin.getLogger().warning("Group " + group.name + ": skipping objective " + name
						+ " (unknown criteria " + map.get("criteria") + ")");
				continue;
			}
			ObjectiveDef def = group.register(name, kind, str(map.get("display-name")));
			if ("HEARTS".equals(str(map.get("render-type")))) {
				def.renderType = RenderType.HEARTS;
				group.applyRenderType(def);
			}
		}
		if (raw.get("display") instanceof Map<?, ?> display) {
			for (Map.Entry<?, ?> entry : display.entrySet()) {
				DisplaySlot slot = SLOTS.get(String.valueOf(entry.getKey()));
				if (slot != null && group.defs.containsKey(String.valueOf(entry.getValue()))) {
					group.setDisplay(slot, String.valueOf(entry.getValue()));
				}
			}
		}
		for (Object o : list(raw.get("scores"))) {
			Map<?, ?> map = (Map<?, ?>) o;
			String objective = String.valueOf(map.get("objective"));
			if (group.defs.containsKey(objective) && map.get("score") instanceof Number score) {
				group.setScore(objective, String.valueOf(map.get("entry")), score.intValue());
			}
		}
	}

	private static List<?> list(Object o) {
		return o instanceof List<?> l ? l : List.of();
	}

	private static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	// ---- Saving ----

	void save() {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Group group : groups.values()) {
			if (group.defs.isEmpty()) {
				continue;
			}
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("name", group.name);
			List<Map<String, Object>> objectives = new ArrayList<>();
			List<Map<String, Object>> scores = new ArrayList<>();
			for (ObjectiveDef def : group.defs.values()) {
				Map<String, Object> o = new LinkedHashMap<>();
				o.put("name", def.name);
				o.put("criteria", def.kind.id());
				o.put("display-name", def.displayName);
				o.put("render-type", def.renderType.name());
				objectives.add(o);
				if (def.kind.isLive()) {
					continue; // sampled again when the players are in the group
				}
				for (String entry : group.scoreboard.getEntries()) {
					Integer score = group.getScore(def.name, entry);
					if (score != null) {
						Map<String, Object> s = new LinkedHashMap<>();
						s.put("objective", def.name);
						s.put("entry", entry);
						s.put("score", score);
						scores.add(s);
					}
				}
			}
			map.put("objectives", objectives);
			Map<String, Object> display = new LinkedHashMap<>();
			group.shown().forEach((slot, objective) -> display.put(slotKey(slot), objective));
			map.put("display", display);
			map.put("scores", scores);
			out.add(map);
		}
		for (Map<?, ?> orphan : orphanState.values()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> copy = (Map<String, Object>) orphan;
			out.add(copy);
		}

		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("groups", out);
		try {
			Files.createDirectories(stateFile.toPath().getParent());
			File tmp = new File(stateFile.getPath() + ".tmp");
			yaml.save(tmp);
			Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save " + stateFile, e);
		}
	}

	void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	// ---- Players ----

	void applyAll() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			apply(player);
		}
	}

	/** Shows the scoreboard of the player's current group (the main one outside any group) */
	void apply(Player player) {
		Group group = groupOf(player.getWorld());
		var target = group != null ? group.scoreboard : Bukkit.getScoreboardManager().getMainScoreboard();
		if (player.getScoreboard() != target) {
			player.setScoreboard(target);
		}
		refreshLive(player);
	}

	/** Live objectives: value in the player's current group, nothing in the others */
	void refreshLive(Player player) {
		Group current = groupOf(player.getWorld());
		String entry = player.getName();
		for (Group group : groups.values()) {
			for (ObjectiveDef def : group.defs.values()) {
				if (!def.kind.isLive()) {
					continue;
				}
				if (group == current) {
					group.setScore(def.name, entry, def.kind.sample(player));
				} else {
					group.resetScore(def.name, entry);
				}
			}
		}
	}

	void clearLive(Player player) {
		String entry = player.getName();
		for (Group group : groups.values()) {
			for (ObjectiveDef def : group.defs.values()) {
				if (def.kind.isLive()) {
					group.resetScore(def.name, entry);
				}
			}
		}
	}

	void tickLive() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			refreshLive(player);
		}
	}

	/** Puts every player back on the main scoreboard (on shutdown) */
	void restoreMain() {
		var main = Bukkit.getScoreboardManager().getMainScoreboard();
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.setScoreboard(main);
		}
	}

	static String slotKey(DisplaySlot slot) {
		return switch (slot) {
			case PLAYER_LIST -> "list";
			case SIDEBAR -> "sidebar";
			case BELOW_NAME -> "below_name";
			default -> slot.name().toLowerCase(Locale.ROOT);
		};
	}

	static DisplaySlot slot(String name) {
		DisplaySlot slot = SLOTS.get(name.toLowerCase(Locale.ROOT));
		if (slot == null) {
			throw new CommandFailure("Unknown slot '" + name + "' (list, sidebar, below_name)");
		}
		return slot;
	}
}
