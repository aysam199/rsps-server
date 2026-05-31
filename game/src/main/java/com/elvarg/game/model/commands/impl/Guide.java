package com.elvarg.game.model.commands.impl;

import com.elvarg.game.content.ServerGuide;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.commands.Command;

public class Guide implements Command {

    @Override
    public void execute(Player player, String command, String[] parts) {
        ServerGuide.openPopup(player);
    }

    @Override
    public boolean canUse(Player player) {
        return true;
    }

}
