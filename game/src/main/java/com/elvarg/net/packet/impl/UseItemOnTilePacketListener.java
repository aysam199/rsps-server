package com.elvarg.net.packet.impl;

import com.elvarg.game.content.abilities.AbilityHandler;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.net.packet.Packet;
import com.elvarg.net.packet.PacketExecutor;

/**
 * Handles "use item on a map tile" - currently used by ground-targeted ability
 * items (e.g. Dash): the player selects the ability item, then clicks a tile,
 * and the ability fires towards that tile.
 *
 * <p>Packet layout (7 bytes): itemId (short), x (short), y (short), plane (byte).</p>
 *
 * @author Custom
 */
public class UseItemOnTilePacketListener implements PacketExecutor {

    @Override
    public void execute(Player player, Packet packet) {
        if (player == null || player.getHitpoints() <= 0) {
            return;
        }
        int itemId = packet.readShort();
        int x = packet.readShort();
        int y = packet.readShort();
        int plane = packet.readUnsignedByte();

        AbilityHandler.castGroundTarget(player, itemId, new Location(x, y, plane));
    }
}
