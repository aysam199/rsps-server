package com.elvarg.game.content;

import com.elvarg.game.GameConstants;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.dialogues.builders.DialogueChainBuilder;
import com.elvarg.game.model.dialogues.entries.impl.EndDialogue;
import com.elvarg.game.model.dialogues.entries.impl.StatementDialogue;
import com.elvarg.game.task.Task;
import com.elvarg.game.task.TaskManager;

/**
 * New-player onboarding for the server's unique feature - the custom MOBA-style
 * ability system. Without this, a brand-new player has no way to discover what
 * makes the server different.
 *
 * <p>It provides:</p>
 * <ul>
 *   <li>A multi-page "welcome scroll" popup shown on a player's first login.</li>
 *   <li>A few concise chat lines on first login (so the key info is in the chat
 *       log even if they close the popup quickly).</li>
 *   <li>The same popup on demand via the {@code ::guide} command.</li>
 * </ul>
 *
 * @author Custom
 */
public final class ServerGuide {

    private ServerGuide() {
    }

    /**
     * Runs the first-login onboarding: prints the short chat walkthrough now and
     * opens the welcome scroll a couple of ticks later (once the login flow has
     * settled, so the interface isn't immediately overwritten).
     */
    public static void welcomeOnLogin(Player player) {
        sendChat(player);
        TaskManager.submit(new Task(2) {
            @Override
            protected void execute() {
                openPopup(player);
                stop();
            }
        });
    }

    /** Sends the concise, colour-coded walkthrough to the chat box. */
    public static void sendChat(Player player) {
        player.getPacketSender().sendMessage("@red@Welcome to " + GameConstants.NAME
                + "!@bla@ A PvP server with custom MOBA-style abilities.");
        player.getPacketSender().sendMessage("Talk to the @blu@Ability Master@bla@ at home to buy & upgrade abilities, then USE them in combat.");
        player.getPacketSender().sendMessage("Earn gold via PvM, Wilderness PvP, daily logins, and @blu@::vote@bla@ (spend tickets at @blu@::voteshop@bla@).");
        player.getPacketSender().sendMessage("Type @blu@::guide@bla@ at any time to see the full guide.");
    }

    /** Opens the multi-page welcome scroll. Used on first login and by ::guide. */
    public static void openPopup(Player player) {
        DialogueChainBuilder guide = new DialogueChainBuilder();
        guide.add(
                new StatementDialogue(0, "Welcome to " + GameConstants.NAME + "! This is a PvP server built "
                        + "around custom, MOBA-style ability items that you buy, cast, and upgrade."),
                new StatementDialogue(1, "Getting started: talk to the Ability Master at home to buy an "
                        + "ability. Then 'Use' the ability item on an enemy - or on yourself - to cast it."),
                new StatementDialogue(2, "Make it yours: 'Use' an ability item on the Ability Master to upgrade "
                        + "it. Every ability can lower its cooldown, plus improve a trait that suits it "
                        + "(damage, healing, dash distance, or freeze)."),
                new StatementDialogue(3, "Earning power: get gold from monsters, Wilderness PvP kills, and "
                        + "daily logins. Vote with ::vote to earn Vote tickets, then spend them at ::voteshop."),
                new StatementDialogue(4, "That's the gist! Type ::guide at any time to read this again. Now get "
                        + "out there and dominate the Wilderness."),
                new EndDialogue(5)
        );
        player.getDialogueManager().start(guide, 0);
    }
}
