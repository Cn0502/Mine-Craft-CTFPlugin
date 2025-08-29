package lobbymanager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class PlayerJoinListener implements Listener {

    private final LobbyManager lobbyManager;

    public PlayerJoinListener(LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Add player to a team
        lobbyManager.addPlayerToTeam(player);

        // Check if teams are full
        lobbyManager.checkStartConditions();
    }
}