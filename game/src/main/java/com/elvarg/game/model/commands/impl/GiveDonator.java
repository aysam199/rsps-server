package com.elvarg.game.model.commands.impl;

import com.elvarg.game.World;
import com.elvarg.game.content.Donation;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.commands.Command;
import com.elvarg.game.model.rights.PlayerRights;

import java.util.Optional;

/**
 * Grants a donator rank to a player. Usage:
 * {@code ::givedonator <name> [regular|super|uber]} (defaults to regular).
 *
 * <p>This is the manual fulfillment step for donations: after a player pays via
 * the {@link Donation#DONATION_URL} link, an owner runs this command. It's also
 * the exact command a Tebex webstore would call for automatic fulfillment later.</p>
 *
 * @author Cursor (custom content for LeagueScape)
 */
public class GiveDonator implements Command {

    @Override
    public void execute(Player player, String command, String[] parts) {
        if (parts.length < 2) {
            player.getPacketSender().sendMessage("Usage: ::givedonator <name> [regular|super|uber]");
            return;
        }

        // Last argument may be a tier; if it resolves to one, treat it as such and
        // the remaining text as the player name. Otherwise the whole tail is the name.
        Donation.Tier tier = parts.length >= 3 ? Donation.Tier.fromArg(parts[parts.length - 1]) : null;

        String name;
        if (tier != null) {
            // Name is everything between the command and the trailing tier token.
            int tierStart = command.lastIndexOf(parts[parts.length - 1]);
            name = command.substring(parts[0].length() + 1, tierStart).trim();
        } else {
            tier = Donation.Tier.REGULAR;
            name = command.substring(parts[0].length() + 1).trim();
        }

        if (name.isEmpty()) {
            player.getPacketSender().sendMessage("Usage: ::givedonator <name> [regular|super|uber]");
            return;
        }

        Optional<Player> target = World.getPlayerByName(name);
        if (!target.isPresent()) {
            player.getPacketSender().sendMessage("Player '" + name + "' is not online.");
            return;
        }

        Player plr = target.get();
        plr.setDonatorRights(tier.rights);
        plr.getPacketSender().sendRights();
        plr.getPacketSender().sendMessage(
                "@dre@You have been granted @blu@" + tier.displayName + "@dre@ rank. Thank you for supporting LeagueScape!");
        player.getPacketSender().sendMessage(
                "Granted @blu@" + tier.displayName + "@bla@ to " + plr.getUsername() + ".");
    }

    @Override
    public boolean canUse(Player player) {
        PlayerRights rights = player.getRights();
        return (rights == PlayerRights.OWNER || rights == PlayerRights.DEVELOPER);
    }

}
