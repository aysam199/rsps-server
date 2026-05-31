package com.elvarg.game.model.dialogues.builders.impl;

import com.elvarg.game.content.VoteShop;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.dialogues.builders.DynamicDialogueBuilder;
import com.elvarg.game.model.dialogues.entries.impl.OptionDialogue;
import com.elvarg.util.ItemIdentifiers;
import com.elvarg.util.Misc;

/**
 * Lets a player convert their Vote tickets into coins
 * ({@link VoteShop#COINS_PER_TICKET} each) in fixed bundles.
 *
 * @author Custom
 */
public class VoteCoinExchangeDialogue extends DynamicDialogueBuilder {

    @Override
    public void build(Player player) {
        int held = player.getInventory().getAmount(ItemIdentifiers.VOTE_TICKET);

        String per = Misc.insertCommasToNumber(Integer.toString(VoteShop.COINS_PER_TICKET));
        String title = "Exchange tickets (" + held + " held, " + per + " gp each)";

        add(new OptionDialogue(0, title, (option) -> {
            switch (option) {
                case FIRST_OPTION:
                    VoteShop.exchangeForCoins(player, 1);
                    break;
                case SECOND_OPTION:
                    VoteShop.exchangeForCoins(player, 10);
                    break;
                case THIRD_OPTION:
                    VoteShop.exchangeForCoins(player, Integer.MAX_VALUE);
                    break;
                default:
                    break;
            }
            player.getPacketSender().sendInterfaceRemoval();
        }, "Exchange 1 ticket", "Exchange 10 tickets", "Exchange all tickets", "Cancel"));
    }
}
