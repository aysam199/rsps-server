package com.elvarg.net.packet.impl;

import com.elvarg.game.definition.ItemDefinition;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.container.impl.Bank;
import com.elvarg.net.packet.Packet;
import com.elvarg.net.packet.PacketExecutor;
import com.elvarg.util.Misc;

public class ExamineItemPacketListener implements PacketExecutor {

	@Override
	public void execute(Player player, Packet packet) {
		int itemId = packet.readShort();
		int interfaceId = packet.readInt();

		// Coins
		if (itemId == 995 || itemId == 13307) {
			int amount = player.getInventory().getAmount(itemId);
			if (interfaceId >= Bank.CONTAINER_START && interfaceId < Bank.CONTAINER_START + Bank.TOTAL_BANK_TABS) {
				int fromBankTab = interfaceId - Bank.CONTAINER_START;
				amount = player.getBank(fromBankTab).getAmount(itemId);
			}
			player.getPacketSender().sendMessage("@red@"
					+ Misc.insertCommasToNumber("" + amount + "") + "x coins.");
			return;
		}

		// Blowpipe
		if (itemId == 12926) {
			player.getPacketSender()
					.sendMessage("Fires Dragon darts while coating them with venom. Charges left: "
							+ player.getBlowpipeScales());
			return;
		}

		// Custom ability items describe what they do instead of a generic examine.
		com.elvarg.game.content.abilities.Ability ability =
				com.elvarg.game.content.abilities.Ability.forItem(itemId);
		if (ability != null) {
			player.getPacketSender().sendMessage("@blu@" + ability.getDescription());
			player.getPacketSender().sendMessage("Charges left: @red@"
					+ player.getAbilityUpgrades().getCharges(itemId)
					+ "@bla@ - buy more from the Ability Master.");
			return;
		}

		ItemDefinition itemDef = ItemDefinition.forId(itemId);
		if (itemDef != null) {
			player.getPacketSender().sendMessage(itemDef.getExamine());
		}
	}

}
