package com.elvarg.game.model.commands.impl;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.commands.Command;

import java.util.Arrays;
import java.util.List;

public class Title implements Command {

    private static final List<String> INAPPROPRIATE_TITLES = Arrays.asList("nigger", "ass", "boobs");

    // Rank/staff words are blocked to stop players impersonating staff via titles.
    private static final List<String> IMPERSONATION_TERMS = Arrays.asList(
            "owner", "admin", "administrator", "moderator", "developer",
            "staff", "support", "helper", "official", "server");

    @Override
    public void execute(Player player, String command, String[] parts) {
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            player.getPacketSender().sendMessage("Usage: ::title <your title>");
            return;
        }
        String requested = command.substring(command.indexOf(' ') + 1).trim();
        String lower = requested.toLowerCase();

        if (INAPPROPRIATE_TITLES.stream().anyMatch(lower::contains)) {
            player.getPacketSender().sendMessage("You're not allowed to have that in your title.");
            return;
        }
        if (IMPERSONATION_TERMS.stream().anyMatch(lower::contains)) {
            player.getPacketSender().sendMessage("Titles can't contain staff or rank words.");
            return;
        }
        if (requested.length() > 16) {
            player.getPacketSender().sendMessage("That title is too long (max 16 characters).");
            return;
        }
        player.setLoyaltyTitle("@blu@" + requested);
    }

    @Override
    public boolean canUse(Player player) {
        return true;
    }

}
