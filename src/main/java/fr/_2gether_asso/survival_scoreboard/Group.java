package fr._2gether_asso.survival_scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** A group of worlds with its own scoreboard (a real Bukkit scoreboard, shown to the players in the group) */
final class Group {
	private static final Pattern OBJECTIVE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	final String name;
	final Set<String> worlds;
	final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
	final Map<String, ObjectiveDef> defs = new LinkedHashMap<>();

	Group(String name, Set<String> worlds) {
		this.name = name;
		this.worlds = worlds;
	}

	static void checkObjectiveName(String name) {
		if (!OBJECTIVE_NAME.matcher(name).matches()) {
			throw new CommandFailure("Invalid objective name '" + name + "' (letters, digits, _ and - only)");
		}
	}

	ObjectiveDef def(String objective) {
		ObjectiveDef def = defs.get(objective);
		if (def == null) {
			throw new CommandFailure("Unknown objective '" + objective + "' in group " + name);
		}
		return def;
	}

	/** The base objective of a definition: holds the data, never displayed */
	Objective objective(String name) {
		return scoreboard.getObjective(name);
	}

	/**
	 * A Bukkit objective can only occupy one display slot, but vanilla lets an objective be shown in several.
	 * So each slot shows its own mirror objective ("name.slot") that receives the same scores as the base one.
	 */
	private final Map<DisplaySlot, String> shown = new EnumMap<>(DisplaySlot.class);
	private final Map<DisplaySlot, Objective> mirrors = new EnumMap<>(DisplaySlot.class);

	Map<DisplaySlot, String> shown() {
		return shown;
	}

	private List<Objective> targets(String name) {
		List<Objective> out = new ArrayList<>();
		out.add(objective(name));
		shown.forEach((slot, shownName) -> {
			if (shownName.equals(name)) {
				out.add(mirrors.get(slot));
			}
		});
		return out;
	}

	/** Registers the objective on the scoreboard (fails if it exists) */
	ObjectiveDef register(String objective, CriteriaKind kind, String displayName) {
		checkObjectiveName(objective);
		if (defs.containsKey(objective)) {
			throw new CommandFailure("Objective '" + objective + "' already exists in group " + name);
		}
		ObjectiveDef def = new ObjectiveDef(objective, kind, displayName);
		create(objective, def);
		defs.put(objective, def);
		return def;
	}

	private Objective create(String objectiveName, ObjectiveDef def) {
		// The plugin tracks the values itself: the Bukkit criterion is always dummy
		return scoreboard.registerNewObjective(objectiveName, org.bukkit.scoreboard.Criteria.DUMMY,
				Texts.parse(def.displayName), def.renderType);
	}

	void unregister(String objective) {
		def(objective);
		for (DisplaySlot slot : List.copyOf(shown.keySet())) {
			if (shown.get(slot).equals(objective)) {
				setDisplay(slot, null);
			}
		}
		objective(objective).unregister();
		defs.remove(objective);
	}

	void applyDisplayName(ObjectiveDef def) {
		targets(def.name).forEach(o -> o.displayName(Texts.parse(def.displayName)));
	}

	void applyRenderType(ObjectiveDef def) {
		targets(def.name).forEach(o -> o.setRenderType(def.renderType));
	}

	void setDisplay(DisplaySlot slot, String objective) {
		Objective old = mirrors.remove(slot);
		shown.remove(slot);
		if (old != null) {
			old.unregister(); // also clears the slot
		}
		scoreboard.clearSlot(slot);
		if (objective == null) {
			return;
		}
		ObjectiveDef def = def(objective);
		Objective mirror = create(objective + "." + slot.name().toLowerCase(Locale.ROOT), def);
		for (String entry : scoreboard.getEntries()) {
			Score score = objective(objective).getScore(entry);
			if (score.isScoreSet()) {
				mirror.getScore(entry).setScore(score.getScore());
			}
		}
		mirror.setDisplaySlot(slot);
		mirrors.put(slot, mirror);
		shown.put(slot, objective);
	}

	/** Sets a score (on the base objective and its mirrors) only if it changed (avoids useless packets) */
	void setScore(String objective, String entry, int value) {
		for (Objective o : targets(objective)) {
			Score score = o.getScore(entry);
			if (!score.isScoreSet() || score.getScore() != value) {
				score.setScore(value);
			}
		}
	}

	void resetScore(String objective, String entry) {
		for (Objective o : targets(objective)) {
			Score score = o.getScore(entry);
			if (score.isScoreSet()) {
				score.resetScore();
			}
		}
	}

	void resetAll(String entry) {
		defs.keySet().forEach(name -> resetScore(name, entry));
	}

	/** The score of the base objective, or null if not set */
	Integer getScore(String objective, String entry) {
		Score score = objective(objective).getScore(entry);
		return score.isScoreSet() ? score.getScore() : null;
	}

	/** Adds to the score of every counter objective of this kind */
	boolean increment(CriteriaKind kind, String entry) {
		boolean changed = false;
		for (ObjectiveDef def : List.copyOf(defs.values())) {
			if (def.kind == kind) {
				Integer current = getScore(def.name, entry);
				setScore(def.name, entry, (current == null ? 0 : current) + 1);
				changed = true;
			}
		}
		return changed;
	}
}
