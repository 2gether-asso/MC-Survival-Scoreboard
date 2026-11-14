package fr._2gether_asso.survival_scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.RenderType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /mvsb &lt;group&gt; objectives|players ..., mirroring vanilla /scoreboard but scoped to a group.
 * Works from the console and RCON, so setup scripts can drive it.
 */
final class MvsbCommand implements CommandExecutor, TabCompleter {
	private static final List<String> TOP = List.of("groups", "reload", "save");
	private static final List<String> OBJECTIVE_ACTIONS = List.of("list", "add", "ensure", "remove", "modify", "setdisplay");
	private static final List<String> PLAYER_ACTIONS = List.of("list", "get", "set", "add", "remove", "reset");

	private final GroupManager manager;

	MvsbCommand(GroupManager manager) {
		this.manager = manager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		try {
			run(sender, args);
		} catch (CommandFailure e) {
			sender.sendMessage("Error: " + e.getMessage());
		}
		return true;
	}

	private void run(CommandSender sender, String[] args) {
		if (args.length == 0) {
			sender.sendMessage("Usage: /mvsb groups | reload | save | <group> objectives|players ...");
			return;
		}
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "groups" -> {
				manager.groups().values().forEach(g -> sender.sendMessage(g.name + ": " + String.join(", ", g.worlds)
						+ " (" + g.defs.size() + " objective(s))"));
				return;
			}
			case "reload" -> {
				manager.reload();
				sender.sendMessage("Reloaded " + manager.groups().size() + " group(s).");
				return;
			}
			case "save" -> {
				manager.save();
				sender.sendMessage("Saved.");
				return;
			}
			default -> {
			}
		}
		Group group = manager.group(args[0]);
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		if (rest.length == 0) {
			throw new CommandFailure("Usage: /mvsb " + group.name + " objectives|players ...");
		}
		switch (rest[0].toLowerCase(Locale.ROOT)) {
			case "objectives" -> objectives(sender, group, Arrays.copyOfRange(rest, 1, rest.length));
			case "players" -> players(sender, group, Arrays.copyOfRange(rest, 1, rest.length));
			default -> throw new CommandFailure("Expected 'objectives' or 'players', got '" + rest[0] + "'");
		}
	}

	// ---- objectives ----

	private void objectives(CommandSender sender, Group group, String[] a) {
		String action = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
		switch (action) {
			case "list" -> {
				if (group.defs.isEmpty()) {
					sender.sendMessage("Group " + group.name + " has no objectives.");
				}
				for (ObjectiveDef def : group.defs.values()) {
					sender.sendMessage(def.name + " [" + def.kind.id() + "] display=" + def.displayName
							+ " render=" + def.renderType.name().toLowerCase(Locale.ROOT)
							+ shown(group, def.name));
				}
			}
			case "add" -> {
				need(a, 3, "objectives add <name> <criteria> [displayName]");
				ObjectiveDef def = group.register(a[1], kind(a[2]), join(a, 3));
				manager.markDirty();
				sender.sendMessage("Created objective " + def.name + " in group " + group.name);
			}
			case "ensure" -> {
				need(a, 3, "objectives ensure <name> <criteria> [displayName]");
				ensure(sender, group, a);
			}
			case "remove" -> {
				need(a, 2, "objectives remove <name>");
				group.unregister(a[1]);
				manager.markDirty();
				sender.sendMessage("Removed objective " + a[1] + " from group " + group.name);
			}
			case "modify" -> {
				need(a, 4, "objectives modify <name> displayname <text> | rendertype <integer|hearts>");
				ObjectiveDef def = group.def(a[1]);
				switch (a[2].toLowerCase(Locale.ROOT)) {
					case "displayname" -> {
						Texts.parse(join(a, 3)); // validate first
						def.displayName = join(a, 3);
						group.applyDisplayName(def);
					}
					case "rendertype" -> {
						def.renderType = switch (a[3].toLowerCase(Locale.ROOT)) {
							case "integer" -> RenderType.INTEGER;
							case "hearts" -> RenderType.HEARTS;
							default -> throw new CommandFailure("Render type must be integer or hearts");
						};
						group.applyRenderType(def);
					}
					default -> throw new CommandFailure("Expected 'displayname' or 'rendertype'");
				}
				manager.markDirty();
				sender.sendMessage("Modified objective " + def.name);
			}
			case "setdisplay" -> {
				need(a, 2, "objectives setdisplay <list|sidebar|below_name> [objective]");
				DisplaySlot slot = GroupManager.slot(a[1]);
				group.setDisplay(slot, a.length > 2 ? a[2] : null);
				manager.markDirty();
				sender.sendMessage(a.length > 2 ? "Showing " + a[2] + " in " + a[1] : "Cleared " + a[1]);
			}
			default -> throw new CommandFailure("Unknown action '" + a[0] + "' (" + String.join(", ", OBJECTIVE_ACTIONS) + ")");
		}
	}

	/** Idempotent: creates the objective, or updates its display name; the criteria cannot change */
	private void ensure(CommandSender sender, Group group, String[] a) {
		CriteriaKind kind = kind(a[2]);
		String display = join(a, 3);
		ObjectiveDef existing = group.defs.get(a[1]);
		if (existing == null) {
			group.register(a[1], kind, display);
			manager.markDirty();
			sender.sendMessage("Created objective " + a[1]);
		} else if (existing.kind != kind) {
			throw new CommandFailure("Objective " + a[1] + " has criteria " + existing.kind.id()
					+ ", not " + kind.id() + " (remove it first)");
		} else if (display != null && !display.equals(existing.displayName)) {
			Texts.parse(display);
			existing.displayName = display;
			group.applyDisplayName(existing);
			manager.markDirty();
			sender.sendMessage("Updated the display name of " + a[1]);
		} else {
			sender.sendMessage("Objective " + a[1] + " is up to date");
		}
	}

	private static String shown(Group group, String objective) {
		StringBuilder slots = new StringBuilder();
		group.shown().forEach((slot, name) -> {
			if (name.equals(objective)) {
				slots.append(' ').append(GroupManager.slotKey(slot));
			}
		});
		return slots.isEmpty() ? "" : " shown in:" + slots;
	}

	private static CriteriaKind kind(String name) {
		return CriteriaKind.parse(name).orElseThrow(() -> new CommandFailure("Unsupported criteria '" + name
				+ "' (" + String.join(", ", Arrays.stream(CriteriaKind.values()).map(CriteriaKind::id).toList()) + ")"));
	}

	// ---- players ----

	private void players(CommandSender sender, Group group, String[] a) {
		String action = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
		switch (action) {
			case "list" -> {
				var entries = a.length > 1 ? List.of(a[1]) : new ArrayList<>(group.scoreboard.getEntries());
				entries.forEach(e -> sender.sendMessage(e + ": " + scores(group, e)));
			}
			case "get" -> {
				need(a, 3, "players get <entry> <objective>");
				group.def(a[2]);
				Integer score = group.getScore(a[2], a[1]);
				sender.sendMessage(score != null ? a[1] + " has " + score + " [" + a[2] + "]"
						: "Can't get " + a[2] + " for " + a[1] + ": no score set");
			}
			case "set", "add", "remove" -> {
				need(a, 4, "players " + action + " <entry> <objective> <value>");
				ObjectiveDef def = group.def(a[2]);
				if (def.kind.isLive()) {
					throw new CommandFailure(def.name + " is a live " + def.kind.id() + " objective, its value is automatic");
				}
				int value = number(a[3]);
				if (!action.equals("set") && value < 0) {
					throw new CommandFailure("Value must be positive for " + action);
				}
				Integer stored = group.getScore(def.name, a[1]);
				int current = stored == null ? 0 : stored;
				int result = switch (action) {
					case "set" -> value;
					case "add" -> current + value;
					default -> current - value;
				};
				group.setScore(def.name, a[1], result);
				manager.markDirty();
				sender.sendMessage(a[1] + " now has " + result + " [" + def.name + "]");
			}
			case "reset" -> {
				need(a, 2, "players reset <entry> [objective]");
				if (a.length > 2) {
					group.resetScore(group.def(a[2]).name, a[1]);
				} else {
					group.resetAll(a[1]);
				}
				manager.markDirty();
				sender.sendMessage("Reset " + a[1]);
			}
			default -> throw new CommandFailure("Unknown action '" + a[0] + "' (" + String.join(", ", PLAYER_ACTIONS) + ")");
		}
	}

	private static String scores(Group group, String entry) {
		List<String> out = new ArrayList<>();
		for (ObjectiveDef def : group.defs.values()) {
			Integer score = group.getScore(def.name, entry);
			if (score != null) {
				out.add(def.name + "=" + score);
			}
		}
		return out.isEmpty() ? "(no scores)" : String.join(", ", out);
	}

	// ---- helpers ----

	private static void need(String[] a, int count, String usage) {
		if (a.length < count) {
			throw new CommandFailure("Usage: /mvsb <group> " + usage);
		}
	}

	private static String join(String[] a, int from) {
		return a.length > from ? String.join(" ", Arrays.copyOfRange(a, from, a.length)) : null;
	}

	private static int number(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new CommandFailure("Expected an integer, got '" + s + "'");
		}
	}

	// ---- tab completion ----

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		String last = args[args.length - 1].toLowerCase(Locale.ROOT);
		List<String> options = switch (args.length) {
			case 1 -> Stream.concat(TOP.stream(), manager.groups().keySet().stream()).toList();
			case 2 -> manager.groups().containsKey(args[0]) ? List.of("objectives", "players") : List.of();
			case 3 -> args[1].equalsIgnoreCase("objectives") ? OBJECTIVE_ACTIONS
					: args[1].equalsIgnoreCase("players") ? PLAYER_ACTIONS : List.of();
			default -> complete(args);
		};
		return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
	}

	private List<String> complete(String[] a) {
		Group group = manager.groups().get(a[0]);
		if (group == null) {
			return List.of();
		}
		List<String> objectives = new ArrayList<>(group.defs.keySet());
		String kind = a[1].toLowerCase(Locale.ROOT);
		String action = a[2].toLowerCase(Locale.ROOT);
		int pos = a.length; // 1-based position of the argument being typed
		if (kind.equals("objectives")) {
			return switch (action) {
				case "remove" -> pos == 4 ? objectives : List.of();
				case "ensure", "add" -> pos == 5 ? Arrays.stream(CriteriaKind.values()).map(CriteriaKind::id).toList() : List.of();
				case "modify" -> pos == 4 ? objectives
						: pos == 5 ? List.of("displayname", "rendertype")
						: pos == 6 && a[4].equalsIgnoreCase("rendertype") ? List.of("integer", "hearts") : List.of();
				case "setdisplay" -> pos == 4 ? List.of("list", "sidebar", "below_name") : pos == 5 ? objectives : List.of();
				default -> List.of();
			};
		}
		if (kind.equals("players")) {
			if (pos == 4) {
				return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
			}
			if (pos == 5) {
				return objectives;
			}
		}
		return List.of();
	}
}
