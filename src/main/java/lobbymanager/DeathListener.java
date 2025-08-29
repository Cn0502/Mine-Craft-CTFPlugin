package lobbymanager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class DeathListener implements Listener {

    private final RoundManager roundManager;
    private final Plugin plugin;

    public DeathListener(RoundManager roundManager, Plugin plugin) {
        this.roundManager = roundManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Delay respawn slightly to avoid glitching
        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
                player.setGameMode(GameMode.SPECTATOR);
                player.getInventory().clear();

                // Notify round manager
                roundManager.onPlayerDeath(player);
            }
        }.runTaskLater(plugin, 2L);
    }
}
