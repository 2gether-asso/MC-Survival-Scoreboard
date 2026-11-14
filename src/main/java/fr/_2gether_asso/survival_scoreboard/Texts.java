package fr._2gether_asso.survival_scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

final class Texts {
	private Texts() {
	}

	/**
	 * Display names are stored as typed: a JSON text component (like vanilla, starts with {, [ or ")
	 * or MiniMessage text (e.g. {@code <red>❤}).
	 */
	static Component parse(String raw) {
		String trimmed = raw.trim();
		if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
			try {
				return GsonComponentSerializer.gson().deserialize(trimmed);
			} catch (RuntimeException e) {
				throw new CommandFailure("Invalid JSON text component: " + e.getMessage());
			}
		}
		return MiniMessage.miniMessage().deserialize(trimmed);
	}
}
