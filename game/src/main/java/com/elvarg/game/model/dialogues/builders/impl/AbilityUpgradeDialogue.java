package com.elvarg.game.model.dialogues.builders.impl;

import com.elvarg.game.content.abilities.Ability;
import com.elvarg.game.content.abilities.AbilityHandler;
import com.elvarg.game.content.abilities.UpgradeType;
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
        int secLevel = player.getAbilityUpgrades().getSecondaryLevel(ability.getItemId());
        boolean cdMaxed = cdLevel >= ability.getMaxCooldownLevel();
        boolean secMaxed = secLevel >= ability.getMaxSecondaryLevel();
        UpgradeType type = ability.getSecondaryUpgrade();

        String cooldownGold;
        if (cdMaxed) {
            cooldownGold = "Cooldown: MAXED (-20%)";
        } else {
            long cost = AbilityHandler.withDonatorDiscount(player, AbilityHandler.cooldownUpgradeCost(cdLevel));
            cooldownGold = "Cooldown -0.25s (" + Misc.insertCommasToNumber(Long.toString(cost)) + " gp)";
        }

        String secondaryGold;
        if (secMaxed) {
            secondaryGold = type.getDisplayName() + ": MAXED (" + type.getMaxLabel() + ")";
        } else {
            long cost = AbilityHandler.withDonatorDiscount(player, AbilityHandler.secondaryUpgradeCost(secLevel));
            secondaryGold = type.getDisplayName() + " " + type.getStepLabel()
                    + " (" + Misc.insertCommasToNumber(Long.toString(cost)) + " gp)";
        }

        String cooldownTickets = cdMaxed ? "Cooldown: MAXED (tickets)"
                : "Cooldown -0.25s (" + AbilityHandler.COOLDOWN_TICKET_COST + " vote tickets)";
        String secondaryTickets = secMaxed ? (type.getDisplayName() + ": MAXED (tickets)")
                : type.getDisplayName() + " " + type.getStepLabel()
                        + " (" + AbilityHandler.SECONDARY_TICKET_COST + " vote tickets)";

        add(new OptionDialogue(0, "Upgrade " + ability.getDisplayName(), (option) -> {
            switch (option) {
                case FIRST_OPTION:
                    AbilityHandler.buyCooldownUpgrade(player, ability);
                    break;
                case SECOND_OPTION:
                    AbilityHandler.buySecondaryUpgrade(player, ability);
                    break;
                case THIRD_OPTION:
                    AbilityHandler.buyCooldownUpgradeWithTickets(player, ability);
                    break;
                case FOURTH_OPTION:
                    AbilityHandler.buySecondaryUpgradeWithTickets(player, ability);
                    break;
                default:
                    break;
            }
            player.getPacketSender().sendInterfaceRemoval();
        }, cooldownGold, secondaryGold, cooldownTickets, secondaryTickets, "Cancel"));
    }
}
