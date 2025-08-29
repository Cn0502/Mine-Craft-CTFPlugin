package com.azcwr.ctfplugin;

import lobbymanager.LobbyManager;
import lobbymanager.PlayerJoinListener;
import lobbymanager.DeathListener;
import lobbymanager.RoundManager;
import bombmanager.BombManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Main plugin class for the CTF game mode.
 * Handles initialization of managers, listeners, and commands.
 */
public class CTFPlugin extends JavaPlugin {

    // === Core managers ===
    private LobbyManager lobbyManager;
    private RoundManager roundManager;
    private BombManager bombManager;

    /**
     * Called when the plugin is enabled (server start/reload).
     * Initializes systems and registers event listeners.
     */
    @Override
    public void onEnable() {
        getLogger().info("CTFPlugin enabled.");

        // === Initialize world settings ===
        World world = Bukkit.getWorld("Dust 2 Map");
        if (world != null) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true); // ✅ Prevent item drops
        } else {
            getLogger().warning("World 'Dust 2 Map' not found! keepInventory not set.");
        }

        // === Manager setup ===
        this.lobbyManager = new LobbyManager();
        this.roundManager = new RoundManager(this, lobbyManager, null);
        this.bombManager = new BombManager(this, roundManager);
        this.roundManager.setBombManager(bombManager);

        // === Register event listeners ===
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(lobbyManager), this);
        getServer().getPluginManager().registerEvents(new DeathListener(roundManager, this), this);
        getServer().getPluginManager().registerEvents(bombManager, this);
    }


    /**
     * Called when the plugin is disabled (server stop/reload).
     */
    @Override
    public void onDisable() {
        getLogger().info("CTFPlugin disabled.");
    }

    /**
     * Handles custom plugin commands: /restartctf and /jointeam
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Restrict to players only
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        // === Handle commands ===
        switch (command.getName().toLowerCase()) {

            case "restartctf":
                // Reset round and game state
                roundManager.resetMatch();
                Bukkit.broadcastMessage(ChatColor.YELLOW + "[CTF] Game has been reset.");
                return true;

            case "jointeam":
                // Force assign team for manual testing
                if (args.length != 1) {
                    player.sendMessage(ChatColor.RED + "Usage: /jointeam <red|blue>");
                    return true;
                }

                String team = args[0].toLowerCase();
                if (team.equals("red")) {
                    lobbyManager.forceAddToRed(player);
                    player.sendMessage(ChatColor.RED + "You joined Red Team (forced).");
                } else if (team.equals("blue")) {
                    lobbyManager.forceAddToBlue(player);
                    player.sendMessage(ChatColor.BLUE + "You joined Blue Team (forced).");
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid team. Use red or blue.");
                }
                return true;

            default:
                return false;
        }
    }
}
