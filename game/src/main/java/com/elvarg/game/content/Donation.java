package com.elvarg.game.content;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.rights.DonatorRights;

/**
 * Central configuration + helpers for LeagueScape donations.
 *
 * <p>Donations are intentionally <b>non pay-to-win</b>: the only mechanical perk
 * is a discount on ability upgrades (Regular -10%, Super -20%, Uber -30%), and
 * everyone reaches the same caps regardless. Donators just pay a little less to
 * get there. See {@link com.elvarg.game.content.abilities.AbilityHandler#withDonatorDiscount}.</p>
 *
 * <p><b>How fulfillment works (manual / Option A):</b> a player pays through the
 * {@link #DONATION_URL} link, then an owner/developer grants the rank in-game with
 * {@code ::givedonator <name> <tier>}. This is the exact same command a Tebex
 * webstore would run later, so moving to automatic fulfillment needs no rework.</p>
 *
 * <p><b>To configure:</b> paste your real donation link into {@link #DONATION_URL}
 * and adjust the prices below. Nothing else needs to change.</p>
 *
 * @author Cursor (custom content for LeagueScape)
 */
public final class Donation {

    private Donation() {
    }

    // ------------------------------------------------------------------
    // EDIT ME: payment link + prices (the only values you should change)
    // ------------------------------------------------------------------

    /**
     * Where players go to pay. Use a Ko-fi page or a PayPal.me / PayPal Business
     * link. Heads up: PayPal <i>personal</i> accounts are often frozen for selling
     * virtual game goods, so Ko-fi or a PayPal Business account is safer.
     */
    public static final String DONATION_URL = "https://ko-fi.com/leaguescape";

    /** Donation tiers, cheapest first. Price is a display string (USD). */
    public enum Tier {
        REGULAR("Donator", "$5", DonatorRights.REGULAR_DONATOR, "-10% on abilities & upgrades"),
        SUPER("Super Donator", "$10", DonatorRights.SUPER_DONATOR, "-20% on abilities & upgrades"),
        UBER("Uber Donator", "$20", DonatorRights.UBER_DONATOR, "-30% on abilities & upgrades");

        public final String displayName;
        public final String price;
        public final DonatorRights rights;
        public final String perk;

        Tier(String displayName, String price, DonatorRights rights, String perk) {
            this.displayName = displayName;
            this.price = price;
            this.rights = rights;
            this.perk = perk;
        }

        /** Resolves a tier from a command argument like "super" / "uber" / "reg". */
        public static Tier fromArg(String arg) {
            if (arg == null) {
                return null;
            }
            switch (arg.toLowerCase().trim()) {
                case "regular":
                case "reg":
                case "donator":
                case "1":
                    return REGULAR;
                case "super":
                case "2":
                    return SUPER;
                case "uber":
                case "3":
                    return UBER;
                default:
                    return null;
            }
        }
    }

    // ------------------------------------------------------------------
    // In-game info shown by ::donate
    // ------------------------------------------------------------------

    /** Prints the donation pitch to the player's chatbox and opens the link. */
    public static void showInfo(Player player) {
        player.getPacketSender().sendMessage("@dre@--- Support LeagueScape (optional) ---");
        player.getPacketSender().sendMessage("Never pay-to-win - same caps for all, donors just pay less.");
        for (Tier tier : Tier.values()) {
            player.getPacketSender().sendMessage(
                    "@blu@" + tier.displayName + "@bla@ (" + tier.price + "): " + tier.perk + ".");
        }
        player.getPacketSender().sendMessage("@blu@Supporter@bla@ (any amount): no rank, just our thanks!");
        player.getPacketSender().sendMessage("Donate at: @blu@" + DONATION_URL);
        player.getPacketSender().sendMessage("@red@Put your username (@blu@" + player.getUsername()
                + "@red@) as a note@bla@ on the donation.");
        player.getPacketSender().sendMessage("Ranks are applied manually - contact @blu@aysam@bla@ if needed.");
        // Also pop the link open in the player's browser.
        player.getPacketSender().sendURL(DONATION_URL);
    }
}
