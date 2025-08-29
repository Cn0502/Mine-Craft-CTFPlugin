package bombmanager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import lobbymanager.RoundManager;

import java.util.*;
import java.util.stream.Collectors;

public class BombManager implements Listener {

    private final Plugin plugin;
    private final RoundManager roundManager;

    private boolean bombPlanted = false;
    private boolean bombDefused = false;
    private Location bombLocation = null;
    private int bombExplosionTask = -1;

    // Track who is actively defusing (UUID → defuse task)
    private final Map<UUID, BukkitRunnable> activeDefuseTasks = new HashMap<>();

    public BombManager(Plugin plugin, RoundManager roundManager) {
        this.plugin = plugin;
        this.roundManager = roundManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Ignore off-hand or invalid clicks
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        Location loc = player.getLocation();

        // === BOMB PLANTING ===
        if (!bombPlanted && item != null && item.getType() == Material.TNT && item.hasItemMeta()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if ("Bomb".equals(displayName)) {
                event.setCancelled(true);

                if (!canPlaceBombHere(loc)) {
                    player.sendMessage(ChatColor.RED + "You must be in a bomb site to plant the bomb!");
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + "Planting bomb... Hold still for 3.2 seconds.");

                new BukkitRunnable() {
                    int timeLeft = 64;
                    final Location startLoc = player.getLocation();

                    @Override
                    public void run() {
                        if (!player.isOnline() || !canPlaceBombHere(player.getLocation()) || player.getLocation().distance(startLoc) > 1.5) {
                            player.sendMessage(ChatColor.RED + "Bomb planting interrupted.");
                            cancel();
                            return;
                        }

                        if (--timeLeft <= 0) {
                            bombPlanted = true;
                            bombDefused = false;
                            bombLocation = startLoc.getBlock().getLocation();
                            player.getInventory().removeItem(item);
                            bombLocation.getBlock().setType(Material.TNT);
                            player.getWorld().playSound(startLoc, Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.0f);
                            Bukkit.broadcastMessage(ChatColor.RED + "[CTF] Bomb has been planted!");

                            roundManager.onBombPlanted();

                            // Schedule bomb explosion after 40 seconds
                            bombExplosionTask = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                if (!bombDefused && bombLocation != null) {
                                    bombLocation.getWorld().spawnParticle(Particle.EXPLOSION, bombLocation.clone().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
                                    bombLocation.getWorld().playSound(bombLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);

                                    for (Player nearby : bombLocation.getWorld().getPlayers()) {
                                        if (nearby.getLocation().distance(bombLocation) <= 6) {
                                            nearby.damage(20.0);
                                        }
                                    }

                                    bombLocation.getBlock().setType(Material.AIR);
                                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "[CTF] The bomb has exploded!");
                                    roundManager.onBombExploded();

                                    bombPlanted = false;
                                    bombLocation = null;
                                    bombExplosionTask = -1;
                                }
                            }, 40 * 20L);

                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }

        // === BOMB DEFUSING ===
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (bombPlanted && bombLocation != null && event.getClickedBlock().getLocation().equals(bombLocation)) {
                event.setCancelled(true);

                UUID uuid = player.getUniqueId();
                if (activeDefuseTasks.containsKey(uuid)) return; // already defusing

                int defuseTime = hasDefuseKit(player) ? 5 : 10;
                startDefuse(player, defuseTime);
            }
        }
    }

    /**
     * Starts the defusing task for a player.
     * Cancels if they move off the block, or completes after timer ends.
     */
    private void startDefuse(Player player, int seconds) {
        UUID uuid = player.getUniqueId();
        Block startBlock = player.getLocation().getBlock();

        // Show initial defuse message
        Bukkit.broadcastMessage(ChatColor.YELLOW + "[CTF] " + player.getName() + " is defusing the bomb...");

        // ✅ The defuse timer is declared inside the anonymous class
        BukkitRunnable task = new BukkitRunnable() {
            int timeLeft = seconds;

            @Override
            public void run() {
                // Cancel defuse if player moved off the block or logged out
                if (!player.isOnline() || !player.getLocation().getBlock().equals(startBlock)) {
                    player.sendMessage(ChatColor.RED + "Defuse canceled.");
                    player.resetTitle();
                    activeDefuseTasks.remove(uuid);
                    cancel();
                    return;
                }

                // Complete defuse
                if (timeLeft-- <= 0) {
                    if (bombExplosionTask != -1) {
                        Bukkit.getScheduler().cancelTask(bombExplosionTask);
                        bombExplosionTask = -1;
                    }

                    player.sendMessage(ChatColor.GREEN + "You defused the bomb!");
                    Bukkit.broadcastMessage(ChatColor.GREEN + "[CTF] Bomb has been defused!");
                    bombLocation.getBlock().setType(Material.AIR);
                    bombDefused = true;
                    bombPlanted = false;
                    bombLocation = null;

                    roundManager.onBombDefused();
                    activeDefuseTasks.remove(uuid);
                    player.resetTitle();
                    cancel();
                } else {
                    // ✅ Correct usage of timeLeft inside the inner class
                    player.sendTitle(
                            ChatColor.YELLOW + "Defusing...",
                            ChatColor.GRAY + String.valueOf(timeLeft + 1) + "s left",
                            0, 20, 0
                    );
                }
            }
        };

        // Store the task so we can cancel it later
        activeDefuseTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }


    private boolean hasDefuseKit(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (item != null && item.getType() == Material.SHEARS && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase("Defuse Kit")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canPlaceBombHere(Location location) {
        World adaptedWorld = BukkitAdapter.adapt(location.getWorld());
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(adaptedWorld);
        if (regionManager == null) return false;

        BlockVector3 vector = BukkitAdapter.asBlockVector(location);
        ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);
        Set<String> regionIds = regions.getRegions().stream()
                .map(ProtectedRegion::getId)
                .collect(Collectors.toSet());

        return regionIds.contains("bombsite_a") || regionIds.contains("bombsite_b");
    }

    public void clearBombSite() {
        if (bombLocation != null && bombLocation.getBlock().getType() == Material.TNT) {
            bombLocation.getBlock().setType(Material.AIR);
        }

        bombLocation = null;
        bombPlanted = false;
        bombDefused = false;

        if (bombExplosionTask != -1) {
            Bukkit.getScheduler().cancelTask(bombExplosionTask);
            bombExplosionTask = -1;
        }

        // Cancel any active defuse tasks
        for (BukkitRunnable task : activeDefuseTasks.values()) {
            task.cancel();
        }
        activeDefuseTasks.clear();
    }

    public boolean isBombDefused() {
        return bombDefused;
    }

    public boolean isBombPlanted() {
        return bombPlanted;
    }
}
