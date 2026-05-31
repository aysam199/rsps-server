package com.elvarg.game.model.dialogues.builders.impl;

import com.elvarg.game.content.abilities.Ability;
import com.elvarg.game.content.abilities.AbilityHandler;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.dialogues.builders.DynamicDialogueBuilder;
import com.elvarg.game.model.dialogues.entries.impl.OptionDialogue;
import com.elvarg.util.Misc;

/**
 * Chatbox menu for upgrading a single ability. Opened by "using" an ability
 * item on the Ability Master NPC.
 * <p>
 * Players can spend coins to either reduce the ability's cooldown (-0.25s per
 * level, capped at -20%) or increase its damage (+4% per level, capped at
 * +20%). Donators receive a discount on the cost.
 *
 * @author Cursor (custom content for RspsApp)
 */
public class AbilityUpgradeDialogue extends DynamicDialogueBuilder {

    private final Ability ability;

    public AbilityUpgradeDialogue(Ability ability) {
        this.ability = ability;
    }

    @Override
    public void build(Player player) {
        int cdLevel = player.getAbilityUpgrades().getCooldownLevel(ability.getItemId());
        int dmgLevel = player.getAbilityUpgrades().getDamageLevel(ability.getItemId());
        int maxCd = ability.getMaxCooldownLevel();

        String cooldownOption;
        if (cdLevel >= maxCd) {
            cooldownOption = "Cooldown: MAXED (-20%)";
        } else {
            long cost = AbilityHandler.withDonatorDiscount(player, AbilityHandler.cooldownUpgradeCost(cdLevel));
            cooldownOption = "Cooldown -0.25s (" + Misc.insertCommasToNumber(Long.toString(cost)) + " gp)";
        }

        String damageOption;
        if (dmgLevel >= Ability.MAX_DAMAGE_LEVEL) {
            damageOption = "Damage: MAXED (+20%)";
        } else {
            long cost = AbilityHandler.withDonatorDiscount(player, AbilityHandler.damageUpgradeCost(dmgLevel));
            damageOption = "Damage +4% (" + Misc.insertCommasToNumber(Long.toString(cost)) + " gp)";
        }

        add(new OptionDialogue(0, "Upgrade " + ability.getDisplayName(), (option) -> {
            switch (option) {
                case FIRST_OPTION:
                    AbilityHandler.buyCooldownUpgrade(player, ability);
                    player.getPacketSender().sendInterfaceRemoval();
                    break;
                case SECOND_OPTION:
                    AbilityHandler.buyDamageUpgrade(player, ability);
                    player.getPacketSender().sendInterfaceRemoval();
                    break;
                default:
                    player.getPacketSender().sendInterfaceRemoval();
                    break;
            }
        }, cooldownOption, damageOption, "Cancel"));
    }
}
