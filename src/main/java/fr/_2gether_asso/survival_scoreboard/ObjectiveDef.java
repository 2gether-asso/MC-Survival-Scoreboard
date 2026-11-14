package fr._2gether_asso.survival_scoreboard;

import org.bukkit.scoreboard.RenderType;

/** Definition of a group objective, as saved on disk */
final class ObjectiveDef {
	final String name;
	final CriteriaKind kind;
	String displayName; // raw text, see Texts.parse (the objective name when not set)
	RenderType renderType = RenderType.INTEGER;

	ObjectiveDef(String name, CriteriaKind kind, String displayName) {
		this.name = name;
		this.kind = kind;
		this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
	}
}
