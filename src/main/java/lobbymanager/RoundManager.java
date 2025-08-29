package lobbymanager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import bombmanager.BombManager;

import java.util.*;

public class RoundManager {
    private final Plugin plugin;
    private final LobbyManager lobbyManager;
    private BombManager bombManager;

    private final List<Player> redTeam;
    private final List<Player> blueTeam;
    private final Set<Player> alivePlayers = new HashSet<>();

    private int redScore = 0;
    private int blueScore = 0;
    private int roundNumber = 1;
    private boolean redAttacking = true;
    private boolean roundInProgress = false;
    private boolean bombPlanted = false;
    private boolean bombDefused = false;
    private final int maxRoundsToWin = 13;

    public RoundManager(Plugin plugin, LobbyManager lobbyManager, BombManager bombManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
        this.bombManager = bombManager;
        this.redTeam = lobbyManager.getRedTeam();
        this.blueTeam = lobbyManager.getBlueTeam();
    }

    public void setBombManager(BombManager bombManager) {
        this.bombManager = bombManager;
    }

    public void startRound() {
        bombManager.clearBombSite(); // Clear any leftovers before the round starts

        roundInProgress = true;
        bombPlanted = false;
        bombDefused = false;
        Bukkit.broadcastMessage(ChatColor.GOLD + "[CTF] Starting Round " + roundNumber);

        alivePlayers.clear();
        resetPlayers(redTeam);
        resetPlayers(blueTeam);
        alivePlayers.addAll(redTeam);
        alivePlayers.addAll(blueTeam);

        if (redAttacking) {
            giveBombToRandomPlayer(redTeam);
        } else {
            giveBombToRandomPlayer(blueTeam);
        }

        // Round timeout (120s)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (roundInProgress && !bombPlanted) {
                    Bukkit.broadcastMessage(ChatColor.GRAY + "[CTF] Round timed out! Defenders win.");
                    if (redAttacking) addScore("blue"); else addScore("red");
                    endRound();
                }
            }
        }.runTaskLater(plugin, 120 * 20L);
    }

    public void endRound() {
        bombManager.clearBombSite();

        roundInProgress = false;
        roundNumber++;

        if (redScore == maxRoundsToWin || blueScore == maxRoundsToWin) {
            Bukkit.broadcastMessage(ChatColor.DARK_GREEN + "[CTF] " + (redScore == maxRoundsToWin ? "Red" : "Blue") + " Team wins the match!");
            redTeam.clear();
            blueTeam.clear();
            return;
        }

        if (roundNumber == 13) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[CTF] Switching sides after 12 rounds!");
            redAttacking = !redAttacking;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                startRound();
            }
        }.runTaskLater(plugin, 5 * 20L);
    }

    public void resetMatch() {
        bombManager.clearBombSite();

        roundInProgress = false;
        roundNumber = 1;
        redScore = 0;
        blueScore = 0;
        redAttacking = true;
        bombPlanted = false;
        bombDefused = false;
        alivePlayers.clear();

        Bukkit.broadcastMessage(ChatColor.YELLOW + "[CTF] Match has been reset.");

        for (Player player : redTeam) {
            player.getInventory().clear();
            player.teleport(new Location(Bukkit.getWorld("Dust 2 Map"), -43, 23, -76));
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
            player.setFoodLevel(20);
        }

        for (Player player : blueTeam) {
            player.getInventory().clear();
            player.teleport(new Location(Bukkit.getWorld("Dust 2 Map"), -126, 16, -100));
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
            player.setFoodLevel(20);
        }

        startRound();
    }

    public void onPlayerDeath(Player player) {
        alivePlayers.remove(player);
        player.setGameMode(GameMode.SPECTATOR);

        boolean redAlive = alivePlayers.stream().anyMatch(redTeam::contains);
        boolean blueAlive = alivePlayers.stream().anyMatch(blueTeam::contains);

        if (!redAlive && !bombPlanted) {
            Bukkit.broadcastMessage(ChatColor.BLUE + "[CTF] All attackers eliminated! Defenders win.");
            if (redAttacking) addScore("blue"); else addScore("red");
            endRound();
        } else if (!blueAlive && bombPlanted) {
            Bukkit.broadcastMessage(ChatColor.RED + "[CTF] Defenders eliminated. Waiting for bomb to explode...");
        } else if (!blueAlive) {
            Bukkit.broadcastMessage(ChatColor.RED + "[CTF] All defenders eliminated! Attackers win.");
            if (redAttacking) addScore("red"); else addScore("blue");
            endRound();
        }
    }

    public void onBombPlanted() {
        bombPlanted = true;
    }

    public void onBombDefused() {
        bombDefused = true;
        if (roundInProgress) {
            Bukkit.broadcastMessage(ChatColor.BLUE + "[CTF] Bomb defused! Defenders win.");
            if (redAttacking) addScore("blue"); else addScore("red");
            endRound();
        }
    }

    public void onBombExploded() {
        if (roundInProgress) {
            Bukkit.broadcastMessage(ChatColor.RED + "[CTF] Bomb exploded! Attackers win.");
            if (redAttacking) addScore("red"); else addScore("blue");
            endRound();
        }
    }

    public void addScore(String team) {
        if (team.equalsIgnoreCase("red")) {
            redScore++;
        } else {
            blueScore++;
        }
        Bukkit.broadcastMessage(ChatColor.AQUA + "[CTF] Score - Red: " + redScore + " | Blue: " + blueScore);
    }

    private void resetPlayers(List<Player> team) {
        for (Player player : team) {
            player.getInventory().clear();
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(getTeamSpawn(player));
            giveStarterKit(player);
        }
    }

    private void giveStarterKit(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.BOW));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 16));
        player.getInventory().setArmorContents(new ItemStack[]{
                new ItemStack(Material.LEATHER_BOOTS),
                new ItemStack(Material.LEATHER_LEGGINGS),
                new ItemStack(Material.LEATHER_CHESTPLATE),
                new ItemStack(Material.LEATHER_HELMET)
        });
    }

    private void giveBombToRandomPlayer(List<Player> team) {
        if (team.isEmpty()) return;
        Player bombCarrier = team.get(new Random().nextInt(team.size()));
        ItemStack bomb = new ItemStack(Material.TNT);
        ItemMeta meta = bomb.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Bomb");
            bomb.setItemMeta(meta);
        }
        bombCarrier.getInventory().addItem(bomb);
        bombCarrier.sendMessage(ChatColor.RED + "You are carrying the bomb!");
    }

    private Location getTeamSpawn(Player player) {
        if (redAttacking) {
            return redTeam.contains(player)
                    ? new Location(Bukkit.getWorld("Dust 2 Map"), -43, 23, -76)
                    : new Location(Bukkit.getWorld("Dust 2 Map"), -126, 16, -100);
        } else {
            return redTeam.contains(player)
                    ? new Location(Bukkit.getWorld("Dust 2 Map"), -126, 16, -100)
                    : new Location(Bukkit.getWorld("Dust 2 Map"), -43, 23, -76);
        }
    }

    public boolean isRoundInProgress() {
        return roundInProgress;
    }

    public Set<Player> getAlivePlayers() {
        return alivePlayers;
    }
}
