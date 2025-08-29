package lobbymanager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LobbyManager {
    private final List<Player> redTeam = new ArrayList<>();
    private final List<Player> blueTeam = new ArrayList<>();
    private boolean gameStarted = false;
    private static final int TEAM_SIZE = 5;

    public void addPlayerToTeam(Player player) {
        if (redTeam.size() <= blueTeam.size()) {
            redTeam.add(player);
            player.sendMessage(ChatColor.RED + "You have been added to the Red Team.");
        } else {
            blueTeam.add(player);
            player.sendMessage(ChatColor.BLUE + "You have been added to the Blue Team.");
        }
    }

    public void checkStartConditions() {
        if (!gameStarted && redTeam.size() >= TEAM_SIZE && blueTeam.size() >= TEAM_SIZE) {
            gameStarted = true;
            // Start the game
        }
    }

    public List<Player> getRedTeam() {
        return redTeam;
    }

    public List<Player> getBlueTeam() {
        return blueTeam;
    }

    public void forceAddToRed(Player player) {
        redTeam.add(player);
    }

    public void forceAddToBlue(Player player) {
        blueTeam.add(player);
    }

    public void resetGame() {
        redTeam.clear();
        blueTeam.clear();
        gameStarted = false;
    }
}