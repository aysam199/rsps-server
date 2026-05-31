package com.elvarg.game.model.commands.impl;

import com.elvarg.game.World;
import com.elvarg.game.content.abilities.Ability;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.commands.Command;
import com.elvarg.game.model.rights.PlayerRights;

/**
 * Developer/testing command that hands out all custom ability items and spawns
 * a few practice dummies so abilities can be tried out solo.
 * <p>
 * Usage: {@code ::abilities}
 *
 * @author Cursor (custom content for RspsApp)
 */
public class AbilityKit implements Command {

    /** NPC id used as a practice dummy (a passive "Man"). */
    private static final int DUMMY_NPC = 1;

    @Override
    public void execute(Player player, String command, String[] parts) {
        for (Ability ability : Ability.values()) {
            player.getInventory().add(ability.getItemId(), 1);
            // Hand out a big stack of charges so testing isn't interrupted by rebuys.
            player.getAbilityUpgrades().setCharges(ability.getItemId(), 100_000);
        }

        // Spawn a few dummies around the player to test on.
        Location base = player.getLocation();
        int[][] offsets = {{2, 0}, {2, 1}, {3, 0}};
        for (int[] offset : offsets) {
            NPC dummy = NPC.create(DUMMY_NPC, base.transform(offset[0], offset[1]));
            World.getAddNPCQueue().add(dummy);
            if (player.getPrivateArea() != null) {
                player.getPrivateArea().add(dummy);
            }
        }

        player.getPacketSender().sendMessage("You receive all " + Ability.values().length
                + " ability items and some practice dummies.");
        player.getPacketSender().sendMessage("@red@Use@bla@ an item on an enemy (or yourself for self-abilities) to cast it.");
        player.getPacketSender().sendMessage("@blu@Buy & upgrade abilities@bla@ from the Ability Master at home (north of spawn).");
        player.getPacketSender().sendMessage("To upgrade: @red@Use@bla@ an ability item on the Ability Master.");
    }

    @Override
    public boolean canUse(Player player) {
        // Staff-only: this hands out all abilities for free, so it must never be
        // available to regular players (it would bypass the entire gold economy).
        PlayerRights rights = player.getRights();
        return rights == PlayerRights.OWNER || rights == PlayerRights.DEVELOPER;
    }
}
