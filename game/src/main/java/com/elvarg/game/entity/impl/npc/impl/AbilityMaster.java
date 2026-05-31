package com.elvarg.game.entity.impl.npc.impl;

import java.util.Arrays;
import java.util.LinkedHashMap;

import com.elvarg.game.content.abilities.Ability;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.entity.impl.npc.NPCInteraction;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Ids;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.container.shop.Shop;
import com.elvarg.game.model.container.shop.ShopManager;
import com.elvarg.game.model.dialogues.builders.DialogueChainBuilder;
import com.elvarg.game.model.dialogues.builders.impl.AbilityUpgradeDialogue;
import com.elvarg.game.model.dialogues.entries.impl.EndDialogue;
import com.elvarg.game.model.dialogues.entries.impl.NpcDialogue;
import com.elvarg.game.model.dialogues.entries.impl.OptionsDialogue;
import com.elvarg.util.NpcIdentifiers;
import com.elvarg.util.ShopIdentifiers;

/**
 * The "Ability Master" - the hub NPC for the custom ability content.
 * <ul>
 *   <li><b>Talk-to</b> for help, or to browse / buy ability items.</li>
 *   <li><b>Trade</b> to open the Ability Emporium shop directly.</li>
 *   <li><b>Use an ability item on him</b> to open that ability's upgrade menu.</li>
 * </ul>
 *
 * @author Cursor (custom content for RspsApp)
 */
@Ids({NpcIdentifiers.WISE_OLD_MAN})
public class AbilityMaster extends NPC implements NPCInteraction {

    private static final Shop ABILITY_SHOP = new Shop(ShopIdentifiers.ABILITY_SHOP, "Ability Emporium",
            Arrays.stream(Ability.values())
                    .map(ability -> new Item(ability.getItemId(), Shop.INFINITY))
                    .toArray(Item[]::new));

    static {
        ShopManager.shops.put(ABILITY_SHOP.getId(), ABILITY_SHOP);
    }

    private DialogueChainBuilder dialogueBuilder;

    public AbilityMaster(int id, Location position) {
        super(id, position);
        buildDialogues();
    }

    @Override
    public void firstOptionClick(Player player, NPC npc) {
        player.getDialogueManager().start(this.dialogueBuilder, 0);
    }

    @Override
    public void secondOptionClick(Player player, NPC npc) {
        ShopManager.open(player, ShopIdentifiers.ABILITY_SHOP);
    }

    @Override
    public void thirdOptionClick(Player player, NPC npc) {
        ShopManager.open(player, ShopIdentifiers.ABILITY_SHOP);
    }

    @Override
    public void forthOptionClick(Player player, NPC npc) {
    }

    @Override
    public void useItemOnNpc(Player player, NPC npc, int itemId, int slot) {
        Ability ability = Ability.forItem(itemId);
        if (ability == null) {
            player.getPacketSender().sendMessage("The Ability Master can only upgrade ability items.");
            return;
        }
        player.getDialogueManager().start(new AbilityUpgradeDialogue(ability));
    }

    private void buildDialogues() {
        this.dialogueBuilder = new DialogueChainBuilder();
        this.dialogueBuilder.add(
                new NpcDialogue(0, NpcIdentifiers.WISE_OLD_MAN,
                        "Greetings, warrior. I deal in the arts of combat abilities."),
                new OptionsDialogue(1, new LinkedHashMap<>() {{
                    put("Buy ability items.", (player) -> ShopManager.open(player, ShopIdentifiers.ABILITY_SHOP));
                    put("How do I upgrade my abilities?", (player) -> player.getDialogueManager().start(dialogueBuilder, 2));
                    put("Tell me about donating.", (player) -> player.getDialogueManager().start(dialogueBuilder, 4));
                }}),

                new NpcDialogue(2, NpcIdentifiers.WISE_OLD_MAN,
                        "Bring me coins and an ability item - simply USE the item on me. "
                                + "You can shorten its cooldown (up to -20%) or sharpen its "
                                + "damage (up to +20%).",
                        (player) -> player.getDialogueManager().start(this.dialogueBuilder, 1)),

                new NpcDialogue(4, NpcIdentifiers.WISE_OLD_MAN,
                        "Supporters of the realm receive a discount on all ability "
                                + "upgrades - never raw power, just a kinder price. Speak to "
                                + "the staff to learn more.",
                        (player) -> player.getDialogueManager().start(this.dialogueBuilder, 1)),

                new EndDialogue(3)
        );
    }
}
