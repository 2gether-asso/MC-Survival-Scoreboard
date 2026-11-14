package fr._2gether_asso.survival_scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

final class ScoreboardListener implements Listener {
	private final SurvivalScoreboardPlugin plugin;
	private final GroupManager manager;

	ScoreboardListener(SurvivalScoreboardPlugin plugin, GroupManager manager) {
		this.plugin = plugin;
		this.manager = manager;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		manager.apply(event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		manager.clearLive(event.getPlayer());
	}

	@EventHandler
	public void onChangedWorld(PlayerChangedWorldEvent event) {
		manager.apply(event.getPlayer());
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		// The respawn location is only applied after the event
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (player.isOnline()) {
				manager.apply(player);
			}
		});
	}

	/** Deaths and kills count in the group of the world where they happen */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		Group group = manager.groupOf(player.getWorld());
		if (group != null && group.increment(CriteriaKind.DEATH_COUNT, player.getName())) {
			manager.markDirty();
		}
		countKill(player);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof Player)) { // players are handled by onPlayerDeath
			countKill(event.getEntity());
		}
	}

	private void countKill(org.bukkit.entity.LivingEntity victim) {
		Player killer = victim.getKiller();
		if (killer == null) {
			return;
		}
		Group group = manager.groupOf(killer.getWorld());
		if (group == null) {
			return;
		}
		boolean changed = group.increment(CriteriaKind.TOTAL_KILL_COUNT, killer.getName());
		if (victim instanceof Player) {
			changed |= group.increment(CriteriaKind.PLAYER_KILL_COUNT, killer.getName());
		}
		if (changed) {
			manager.markDirty();
		}
	}
}
