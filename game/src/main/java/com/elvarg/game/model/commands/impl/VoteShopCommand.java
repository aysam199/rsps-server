package com.elvarg.game.model.commands.impl;

import com.elvarg.game.content.VoteShop;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.commands.Command;

public class VoteShopCommand implements Command {

    @Override
    public void execute(Player player, String command, String[] parts) {
        VoteShop.open(player);
    }

    @Override
    public boolean canUse(Player player) {
        return true;
    }

}
