package fr._2gether_asso.survival_scoreboard;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Criteria supported by group objectives. Vanilla criteria update every scoreboard of the server, so they
 * would count outside the group: the plugin tracks them itself, only for the group concerned.
 * <ul>
 *   <li>live criteria are sampled from the player while they are in the group (and removed otherwise)</li>
 *   <li>counter criteria are incremented by events that happen in the group (and persisted)</li>
 * </ul>
 */
public enum CriteriaKind {
	DUMMY("dummy", null),
	DEATH_COUNT("deathCount", null),
	PLAYER_KILL_COUNT("playerKillCount", null),
	TOTAL_KILL_COUNT("totalKillCount", null),
	HEALTH("health", p -> p.isDead() ? 0 : (int) Math.ceil(p.getHealth())),
	FOOD("food", Player::getFoodLevel),
	LEVEL("level", Player::getLevel),
	XP("xp", Player::getTotalExperience),
	ARMOR("armor", p -> {
		AttributeInstance armor = p.getAttribute(Attribute.ARMOR);
		return armor == null ? 0 : (int) Math.round(armor.getValue());
	}),
	AIR("air", Player::getRemainingAir);

	private final String id;
	private final ToIntFunction<Player> sampler;

	CriteriaKind(String id, ToIntFunction<Player> sampler) {
		this.id = id;
		this.sampler = sampler;
	}

	/** The vanilla name of the criterion */
	public String id() {
		return id;
	}

	/** Live values are sampled while the player is in the group, and removed when they leave it */
	public boolean isLive() {
		return sampler != null;
	}

	public int sample(Player player) {
		return sampler.applyAsInt(player);
	}

	public static Optional<CriteriaKind> parse(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (CriteriaKind kind : values()) {
			if (kind.id.toLowerCase(Locale.ROOT).equals(lower)) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}
}
