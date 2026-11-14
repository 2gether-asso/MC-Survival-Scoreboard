package fr._2gether_asso.survival_scoreboard;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SurvivalScoreboardPlugin extends JavaPlugin {
	private GroupManager manager;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		manager = new GroupManager(this);
		manager.reload();

		getServer().getPluginManager().registerEvents(new ScoreboardListener(this, manager), this);

		MvsbCommand command = new MvsbCommand(manager);
		PluginCommand mvsb = getCommand("mvsb");
		if (mvsb != null) {
			mvsb.setExecutor(command);
			mvsb.setTabCompleter(command);
		}

		long interval = Math.max(1, getConfig().getLong("update-interval-ticks", 2));
		getServer().getScheduler().runTaskTimer(this, manager::tickLive, interval, interval);
		long autosave = Math.max(5, getConfig().getLong("autosave-seconds", 60)) * 20;
		getServer().getScheduler().runTaskTimer(this, manager::saveIfDirty, autosave, autosave);
	}

	@Override
	public void onDisable() {
		if (manager != null) {
			manager.save();
			manager.restoreMain();
		}
	}
}
