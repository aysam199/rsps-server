package com.elvarg.game.content.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import com.elvarg.game.World;
import com.elvarg.game.content.combat.CombatFactory;
import com.elvarg.game.content.combat.CombatFactory.CanAttackResponse;
import com.elvarg.game.content.combat.hit.HitDamage;
import com.elvarg.game.content.combat.hit.HitMask;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.entity.impl.object.GameObject;
import com.elvarg.game.entity.impl.object.ObjectManager;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.collision.RegionManager;
import com.elvarg.game.model.Animation;
import com.elvarg.game.model.ForceMovement;
import com.elvarg.game.model.Graphic;
import com.elvarg.game.model.GraphicHeight;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.Skill;
import com.elvarg.game.model.areas.AreaManager;
import com.elvarg.game.model.areas.impl.PrivateArea;
import com.elvarg.game.task.Task;
import com.elvarg.game.task.TaskManager;
import com.elvarg.game.task.impl.ForceMovementTask;
import com.elvarg.util.timers.TimerKey;

/**
 * The entry point and shared toolbox for the custom MOBA-style ability items.
 * <p>
 * Every ability is triggered by <b>"Use"</b>-ing one of the ability items on a
 * target:
 * <ul>
 *   <li>Use an "enemy" ability item on another player/NPC to cast it at them.</li>
 *   <li>Use a "self" ability item on your own character to cast it on yourself.</li>
 * </ul>
 * This avoids any client-cache edits - the standard "Use" interaction works on
 * every item.
 *
 * @author Cursor (custom content for RspsApp)
 */
public final class AbilityHandler {

    private AbilityHandler() {
    }

    /**
     * Per-player cooldown tracking. Maps a player to an array (indexed by
     * {@link Ability#ordinal()}) of the timestamp (ms) at which each ability
     * becomes usable again. A {@link WeakHashMap} prevents leaking logged-out
     * players.
     */
    private static final Map<Player, long[]> COOLDOWNS = new WeakHashMap<>();

    /**
     * Attempts to handle a "use item on target" interaction as an ability cast.
     *
     * @param caster the player using the item
     * @param target the mobile the item was used on (may be the caster itself)
     * @param item   the item that was used
     * @return {@code true} if the item was an ability item and the interaction
     *         was consumed, {@code false} otherwise (so normal handling can run)
     */
    public static boolean activate(Player caster, Mobile target, Item item) {
        if (caster == null || item == null) {
            return false;
        }

        Ability ability = Ability.forItem(item.getId());
        if (ability == null) {
            return false;
        }

        // From here on the interaction belongs to the ability system.
        if (caster.getHitpoints() <= 0 || caster.isTeleporting()) {
            return true;
        }

        // Cooldown check.
        long remaining = cooldownRemaining(caster, ability);
        if (remaining > 0) {
            caster.getPacketSender().sendMessage(ability.getDisplayName() + " is on cooldown for "
                    + (remaining / 1000 + 1) + " more second(s).");
            return true;
        }

        // Charge check - abilities are consumable.
        if (!hasCharges(caster, ability)) {
            caster.getPacketSender().sendMessage("@red@You're out of " + ability.getDisplayName()
                    + " charges. Buy more from the Ability Master.");
            caster.getInventory().delete(ability.getItemId(), 1);
            return true;
        }

        // Ground-targeted abilities (e.g. Dash) don't fire immediately - they
        // arm and wait for the player to click a destination tile, resolved in
        // the movement packet via handleGroundTarget(). No cooldown is spent yet.
        if (ability.isGroundTargeted()) {
            if (ability == caster.getPendingGroundAbility()) {
                caster.setPendingGroundAbility(null);
                caster.getPacketSender().sendMessage(ability.getDisplayName() + " aiming cancelled.");
                return true;
            }
            caster.setPendingGroundAbility(ability);
            caster.setPendingGroundAbilityTime(System.currentTimeMillis());
            caster.getPacketSender().sendMessage("@blu@" + ability.getDisplayName()
                    + ": click a tile to dash toward it.");
            return true;
        }

        if (ability.targetsEnemy()) {
            if (target == null || target == caster) {
                caster.getPacketSender().sendMessage("You need to use the " + ability.getDisplayName()
                        + " on an enemy.");
                return true;
            }
            if (!canHit(caster, target)) {
                caster.getPacketSender().sendMessage("You can't attack that target here.");
                return true;
            }
            // Enforce the ability's cast range. (No cooldown is spent on a failed cast.)
            if (caster.getLocation().getDistance(target.getLocation()) > ability.getRange()) {
                caster.getPacketSender().sendMessage("That target is out of range for "
                        + ability.getDisplayName() + " (max " + ability.getRange() + " tiles).");
                return true;
            }
            caster.setMobileInteraction(target);
        } else {
            // Self-cast abilities expect the player to use the item on themselves,
            // but we accept any target click for convenience.
            target = caster;
        }

        // Fire the ability and start the cooldown (reduced by any purchased upgrades).
        long cooldownMs = ability.effectiveCooldownMs(caster);
        ability.activate(caster, target);
        setCooldown(caster, ability, cooldownMs);
        int chargesLeft = consumeCharge(caster, ability);
        int cooldownSeconds = (int) (cooldownMs / 1000);
        caster.getPacketSender().sendAbilityCooldown(item.getId(), cooldownSeconds);
        caster.getPacketSender().sendMessage("@blu@" + ability.getDisplayName() + " cast! Ready in "
                + cooldownSeconds + "s. Charges left: " + chargesLeft + ".");
        return true;
    }

    // ------------------------------------------------------------------
    // Cooldown management
    // ------------------------------------------------------------------

    private static long cooldownRemaining(Player player, Ability ability) {
        long[] arr = COOLDOWNS.get(player);
        if (arr == null) {
            return 0;
        }
        return Math.max(0, arr[ability.ordinal()] - System.currentTimeMillis());
    }

    private static void setCooldown(Player player, Ability ability, long cooldownMs) {
        long[] arr = COOLDOWNS.computeIfAbsent(player, p -> new long[Ability.values().length]);
        arr[ability.ordinal()] = System.currentTimeMillis() + cooldownMs;
    }

    // ------------------------------------------------------------------
    // Ground-targeted abilities (e.g. Dash)
    // ------------------------------------------------------------------

    /** How long an armed ground-targeted ability waits for the player's tile click. */
    private static final long GROUND_TARGET_EXPIRY_MS = 15_000;

    /**
     * Resolves a pending ground-targeted ability (e.g. Dash) against the tile
     * the player just clicked. Called from the movement packet listener before
     * normal walking is processed.
     *
     * @return {@code true} if the click was consumed by an ability (so the
     *         player should not walk to it).
     */
    /**
     * Casts a ground-targeted ability (e.g. Dash) directly at the clicked tile.
     * Triggered by the client's "use ability item, then click a tile" flow
     * (item-on-tile packet) - no separate arming step needed.
     *
     * @return {@code true} if the interaction belonged to the ability system.
     */
    public static boolean castGroundTarget(Player player, int itemId, Location clicked) {
        if (player == null || clicked == null) {
            return false;
        }
        Ability ability = Ability.forItem(itemId);
        if (ability == null || !ability.isGroundTargeted()) {
            return false;
        }
        if (!player.getInventory().contains(itemId)) {
            return false;
        }
        if (player.getHitpoints() <= 0 || player.isTeleporting()) {
            return true;
        }

        long remaining = cooldownRemaining(player, ability);
        if (remaining > 0) {
            player.getPacketSender().sendMessage(ability.getDisplayName() + " is on cooldown for "
                    + (remaining / 1000 + 1) + " more second(s).");
            return true;
        }
        if (!hasCharges(player, ability)) {
            player.getPacketSender().sendMessage("@red@You're out of " + ability.getDisplayName()
                    + " charges. Buy more from the Ability Master.");
            player.getInventory().delete(ability.getItemId(), 1);
            return true;
        }

        long cooldownMs = ability.effectiveCooldownMs(player);
        if (!ability.activateAt(player, clicked)) {
            // activateAt already told the player to click a valid tile.
            return true;
        }
        setCooldown(player, ability, cooldownMs);
        int chargesLeft = consumeCharge(player, ability);
        int cooldownSeconds = (int) (cooldownMs / 1000);
        player.getPacketSender().sendAbilityCooldown(ability.getItemId(), cooldownSeconds);
        player.getPacketSender().sendMessage("@blu@" + ability.getDisplayName() + " cast! Ready in "
                + cooldownSeconds + "s. Charges left: " + chargesLeft + ".");
        return true;
    }

    public static boolean handleGroundTarget(Player player, Location clicked) {
        Ability pending = player.getPendingGroundAbility();
        if (pending == null) {
            return false;
        }

        // Drop stale aim requests so a much later click doesn't trigger a
        // surprise dash; let the player walk normally instead.
        if (System.currentTimeMillis() - player.getPendingGroundAbilityTime() > GROUND_TARGET_EXPIRY_MS) {
            player.setPendingGroundAbility(null);
            return false;
        }

        player.setPendingGroundAbility(null);

        if (player.getHitpoints() <= 0 || player.isTeleporting()) {
            return true;
        }

        long cooldownMs = pending.effectiveCooldownMs(player);
        if (!pending.activateAt(player, clicked)) {
            // Couldn't fire (e.g. they clicked their own tile) - re-arm so they
            // can click a valid destination without re-activating the item.
            player.setPendingGroundAbility(pending);
            player.setPendingGroundAbilityTime(System.currentTimeMillis());
            return true;
        }

        setCooldown(player, pending, cooldownMs);
        int chargesLeft = consumeCharge(player, pending);
        int cooldownSeconds = (int) (cooldownMs / 1000);
        player.getPacketSender().sendAbilityCooldown(pending.getItemId(), cooldownSeconds);
        player.getPacketSender().sendMessage("@blu@" + pending.getDisplayName() + " cast! Ready in "
                + cooldownSeconds + "s. Charges left: " + chargesLeft + ".");
        return true;
    }

    // ------------------------------------------------------------------
    // Upgrade shop: pricing, donator discount and purchase logic
    // ------------------------------------------------------------------

    /** Cost (in coins) of the player's next cooldown-reduction level. */
    public static long cooldownUpgradeCost(int currentLevel) {
        return 50_000L * (currentLevel + 1);
    }

    /** Cost (in coins) of the player's next secondary-track level. */
    public static long secondaryUpgradeCost(int currentLevel) {
        return 100_000L * (currentLevel + 1);
    }

    /** Ticket cost of a cooldown-reduction level when paying with Vote tickets. */
    public static final int COOLDOWN_TICKET_COST = 3;

    /** Ticket cost of a secondary-track level when paying with Vote tickets. */
    public static final int SECONDARY_TICKET_COST = 4;

    /**
     * Applies the donator discount to an upgrade cost. Donators reach the same
     * caps as everyone else - they just pay a little less to get there, so it's
     * a perk rather than pay-to-win.
     */
    public static long withDonatorDiscount(Player player, long cost) {
        double discount;
        switch (player.getDonatorRights()) {
            case REGULAR_DONATOR: discount = 0.10; break;
            case SUPER_DONATOR:   discount = 0.20; break;
            case UBER_DONATOR:    discount = 0.30; break;
            default:              discount = 0.0;  break;
        }
        return (long) Math.floor(cost * (1.0 - discount));
    }

    private static boolean spendCoins(Player player, long cost) {
        int coins = player.getInventory().getAmount(com.elvarg.util.ItemIdentifiers.COINS);
        if (cost > Integer.MAX_VALUE || coins < cost) {
            player.getPacketSender().sendMessage("You need @red@"
                    + com.elvarg.util.Misc.insertCommasToNumber(Long.toString(cost))
                    + "@bla@ coins for that upgrade.");
            return false;
        }
        player.getInventory().delete(com.elvarg.util.ItemIdentifiers.COINS, (int) cost);
        return true;
    }

    // ------------------------------------------------------------------
    // Consumable charges (abilities must be rebought when depleted)
    // ------------------------------------------------------------------

    /** Number of casts granted per ability purchase. */
    public static final int CHARGES_PER_PURCHASE = 40;

    /** Whether the player has at least one charge left for {@code ability}. */
    public static boolean hasCharges(Player player, Ability ability) {
        return player.getAbilityUpgrades().getCharges(ability.getItemId()) > 0;
    }

    /**
     * Spends one charge of {@code ability}. When the last charge is used the
     * ability item is removed from the inventory so the player must rebuy it
     * (their purchased upgrades are kept).
     *
     * @return the charges remaining after this cast.
     */
    private static int consumeCharge(Player player, Ability ability) {
        int left = player.getAbilityUpgrades().getCharges(ability.getItemId()) - 1;
        player.getAbilityUpgrades().setCharges(ability.getItemId(), left);
        if (left <= 0) {
            player.getInventory().delete(ability.getItemId(), 1);
            player.getPacketSender().sendMessage("@red@Your " + ability.getDisplayName()
                    + " has run out of charges - buy more from the Ability Master.");
        }
        return Math.max(0, left);
    }

    /**
     * Buys one pack of {@link #CHARGES_PER_PURCHASE} charges of {@code ability}
     * with coins (donator discount applied). Adds the ability item if the player
     * doesn't already hold one; otherwise just tops up the charges.
     */
    public static void purchaseAbility(Player player, Ability ability) {
        purchaseAbility(player, ability, 1);
    }

    /**
     * Buys {@code packs} packs of charges at once (each pack is
     * {@link #CHARGES_PER_PURCHASE} charges). The shop's "buy 1/5/10" options map
     * straight onto this, so buying 5 grants 5 packs for 5x the price.
     */
    public static void purchaseAbility(Player player, Ability ability, int packs) {
        packs = Math.max(1, Math.min(packs, 10));
        long unit = withDonatorDiscount(player, ability.getBuyCost());
        long cost = unit * packs;
        int totalCharges = CHARGES_PER_PURCHASE * packs;
        int coins = player.getInventory().getAmount(com.elvarg.util.ItemIdentifiers.COINS);
        boolean needsItem = !player.getInventory().contains(ability.getItemId());

        if (needsItem && player.getInventory().getFreeSlots() <= 0) {
            player.getPacketSender().sendMessage("You need a free inventory slot to buy a new ability.");
            return;
        }
        if (cost > Integer.MAX_VALUE || coins < cost) {
            player.getPacketSender().sendMessage("You need @red@"
                    + com.elvarg.util.Misc.insertCommasToNumber(Long.toString(cost)) + "@bla@ coins to buy "
                    + totalCharges + " charges of " + ability.getDisplayName() + ".");
            return;
        }

        player.getInventory().delete(com.elvarg.util.ItemIdentifiers.COINS, (int) cost);
        if (needsItem) {
            player.getInventory().add(ability.getItemId(), 1);
        }
        int newCharges = player.getAbilityUpgrades().getCharges(ability.getItemId()) + totalCharges;
        player.getAbilityUpgrades().setCharges(ability.getItemId(), newCharges);
        player.getPacketSender().sendMessage("@gre@Bought " + totalCharges + " charges of "
                + ability.getDisplayName() + " for " + com.elvarg.util.Misc.insertCommasToNumber(Long.toString(cost))
                + " coins. Total charges: " + newCharges + ".");
    }

    /** Attempts to purchase one cooldown-reduction level for {@code ability}. */
    public static void buyCooldownUpgrade(Player player, Ability ability) {
        int level = player.getAbilityUpgrades().getCooldownLevel(ability.getItemId());
        if (level >= ability.getMaxCooldownLevel()) {
            player.getPacketSender().sendMessage(ability.getDisplayName()
                    + "'s cooldown is already at the minimum (-20%).");
            return;
        }
        long cost = withDonatorDiscount(player, cooldownUpgradeCost(level));
        if (!spendCoins(player, cost)) {
            return;
        }
        player.getAbilityUpgrades().setCooldownLevel(ability.getItemId(), level + 1);
        long newCd = ability.effectiveCooldownMs(player);
        player.getPacketSender().sendMessage("@blu@" + ability.getDisplayName()
                + " cooldown upgraded! Now " + String.format("%.2f", newCd / 1000.0) + "s.");
    }

    /** Attempts to purchase one secondary-track level for {@code ability} with coins. */
    public static void buySecondaryUpgrade(Player player, Ability ability) {
        UpgradeType type = ability.getSecondaryUpgrade();
        int level = player.getAbilityUpgrades().getSecondaryLevel(ability.getItemId());
        if (level >= ability.getMaxSecondaryLevel()) {
            player.getPacketSender().sendMessage(ability.getDisplayName() + "'s "
                    + type.getDisplayName().toLowerCase() + " is already maxed (" + type.getMaxLabel() + ").");
            return;
        }
        long cost = withDonatorDiscount(player, secondaryUpgradeCost(level));
        if (!spendCoins(player, cost)) {
            return;
        }
        player.getAbilityUpgrades().setSecondaryLevel(ability.getItemId(), level + 1);
        announceSecondary(player, ability, level + 1, false);
    }

    private static boolean spendTickets(Player player, int cost) {
        int tickets = player.getInventory().getAmount(com.elvarg.util.ItemIdentifiers.VOTE_TICKET);
        if (tickets < cost) {
            player.getPacketSender().sendMessage("You need @red@" + cost
                    + "@bla@ Vote ticket(s) for that upgrade. Vote with ::vote to earn more.");
            return false;
        }
        player.getInventory().delete(com.elvarg.util.ItemIdentifiers.VOTE_TICKET, cost);
        return true;
    }

    /** Attempts to purchase one cooldown-reduction level using Vote tickets. */
    public static void buyCooldownUpgradeWithTickets(Player player, Ability ability) {
        int level = player.getAbilityUpgrades().getCooldownLevel(ability.getItemId());
        if (level >= ability.getMaxCooldownLevel()) {
            player.getPacketSender().sendMessage(ability.getDisplayName()
                    + "'s cooldown is already at the minimum (-20%).");
            return;
        }
        if (!spendTickets(player, COOLDOWN_TICKET_COST)) {
            return;
        }
        player.getAbilityUpgrades().setCooldownLevel(ability.getItemId(), level + 1);
        long newCd = ability.effectiveCooldownMs(player);
        player.getPacketSender().sendMessage("@blu@" + ability.getDisplayName()
                + " cooldown upgraded with tickets! Now " + String.format("%.2f", newCd / 1000.0) + "s.");
    }

    /** Attempts to purchase one secondary-track level using Vote tickets. */
    public static void buySecondaryUpgradeWithTickets(Player player, Ability ability) {
        UpgradeType type = ability.getSecondaryUpgrade();
        int level = player.getAbilityUpgrades().getSecondaryLevel(ability.getItemId());
        if (level >= ability.getMaxSecondaryLevel()) {
            player.getPacketSender().sendMessage(ability.getDisplayName() + "'s "
                    + type.getDisplayName().toLowerCase() + " is already maxed (" + type.getMaxLabel() + ").");
            return;
        }
        if (!spendTickets(player, SECONDARY_TICKET_COST)) {
            return;
        }
        player.getAbilityUpgrades().setSecondaryLevel(ability.getItemId(), level + 1);
        announceSecondary(player, ability, level + 1, true);
    }

    /** Sends the "upgraded!" confirmation describing the new secondary effect. */
    private static void announceSecondary(Player player, Ability ability, int newLevel, boolean tickets) {
        UpgradeType type = ability.getSecondaryUpgrade();
        String effect;
        switch (type) {
            case DAMAGE:   effect = "+" + (newLevel * 4) + "% damage"; break;
            case HEALING:  effect = "+" + (newLevel * 4) + "% healing"; break;
            case DISTANCE: effect = "+" + newLevel + " tile" + (newLevel == 1 ? "" : "s") + " distance"; break;
            case FREEZE:   effect = "+" + newLevel + " tick" + (newLevel == 1 ? "" : "s") + " freeze"; break;
            default:       effect = "upgraded"; break;
        }
        player.getPacketSender().sendMessage("@blu@" + ability.getDisplayName() + " "
                + type.getDisplayName().toLowerCase() + " upgraded" + (tickets ? " with tickets" : "")
                + "! Now " + effect + ".");
    }

    /**
     * Scales a base damage value by the caster's purchased damage level for the
     * given ability.
     */
    public static int scaleDamage(Player caster, Ability ability, int base) {
        if (caster == null || ability == null || ability.getSecondaryUpgrade() != UpgradeType.DAMAGE) {
            return base;
        }
        int level = secondaryLevel(caster, ability);
        if (level <= 0) {
            return base;
        }
        return (int) Math.round(base * (1.0 + 0.04 * level));
    }

    /**
     * Scales a heal value by the caster's purchased Healing level for
     * {@code ability} (only applies to abilities whose secondary track is
     * {@link UpgradeType#HEALING}).
     */
    public static int scaleHeal(Player caster, Ability ability, int base) {
        if (caster == null || ability == null || ability.getSecondaryUpgrade() != UpgradeType.HEALING) {
            return base;
        }
        int level = secondaryLevel(caster, ability);
        if (level <= 0) {
            return base;
        }
        return (int) Math.round(base * (1.0 + 0.04 * level));
    }

    /**
     * Extra freeze/root ticks from the caster's purchased Freeze upgrades (only
     * for abilities whose secondary track is {@link UpgradeType#FREEZE}).
     */
    public static int freezeBonus(Player caster, Ability ability) {
        if (caster == null || ability == null || ability.getSecondaryUpgrade() != UpgradeType.FREEZE) {
            return 0;
        }
        return secondaryLevel(caster, ability);
    }

    /**
     * Extra tiles of travel from the caster's purchased Distance upgrades (only
     * for abilities whose secondary track is {@link UpgradeType#DISTANCE}).
     */
    public static int bonusDistance(Player caster, Ability ability) {
        if (caster == null || ability == null || ability.getSecondaryUpgrade() != UpgradeType.DISTANCE) {
            return 0;
        }
        return secondaryLevel(caster, ability);
    }

    /** The caster's purchased secondary-track level, clamped to the ability's max. */
    private static int secondaryLevel(Player caster, Ability ability) {
        return Math.min(caster.getAbilityUpgrades().getSecondaryLevel(ability.getItemId()),
                ability.getMaxSecondaryLevel());
    }

    // ------------------------------------------------------------------
    // Targeting helpers
    // ------------------------------------------------------------------

    /**
     * Lightweight attack check used for both single-target and AoE abilities.
     * NPCs are always hittable (great for solo testing); players must be in a
     * zone that permits PvP (e.g. the Wilderness).
     */
    public static boolean canHit(Player caster, Mobile target) {
        if (target == null || target == caster) {
            return false;
        }
        if (!CombatFactory.validTarget(caster, target)) {
            return false;
        }
        if (target.isPlayer()) {
            return AreaManager.canAttack(caster, target) == CanAttackResponse.CAN_ATTACK;
        }
        // NPCs: only genuine combat NPCs may be hit - never bankers, shopkeepers,
        // the Ability Master, etc. (those are flagged non-attackable in their defs).
        if (target.isNpc()) {
            return target.getAsNpc().getDefinition() != null
                    && target.getAsNpc().getDefinition().isAttackable();
        }
        return true;
    }

    /**
     * Runs {@code action} for every valid enemy within {@code radius} tiles of
     * {@code center} (players that can be attacked plus NPCs). Capped at
     * {@code maxTargets} to keep big AoEs sane.
     */
    public static void forEachEnemyNear(Player caster, Location center, int radius, int maxTargets,
                                        Consumer<Mobile> action) {
        int hit = 0;
        for (Player p : World.getPlayers()) {
            if (hit >= maxTargets) {
                return;
            }
            if (p == null || p == caster) {
                continue;
            }
            if (p.getLocation().isWithinDistance(center, radius) && canHit(caster, p)) {
                action.accept(p);
                hit++;
            }
        }
        for (NPC npc : World.getNpcs()) {
            if (hit >= maxTargets) {
                return;
            }
            if (npc == null) {
                continue;
            }
            if (npc.getLocation().isWithinDistance(center, radius) && canHit(caster, npc)) {
                action.accept(npc);
                hit++;
            }
        }
    }

    /**
     * Finds the closest valid enemy within {@code radius} of the caster, or
     * {@code null} if none. Used by gap-closer style abilities.
     */
    public static Mobile nearestEnemy(Player caster, int radius) {
        Mobile best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Player p : World.getPlayers()) {
            if (p == null || p == caster) {
                continue;
            }
            int dist = caster.getLocation().getDistance(p.getLocation());
            if (dist <= radius && dist < bestDist && canHit(caster, p)) {
                best = p;
                bestDist = dist;
            }
        }
        for (NPC npc : World.getNpcs()) {
            if (npc == null) {
                continue;
            }
            int dist = caster.getLocation().getDistance(npc.getLocation());
            if (dist <= radius && dist < bestDist && canHit(caster, npc)) {
                best = npc;
                bestDist = dist;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Damage / status helpers
    // ------------------------------------------------------------------

    /**
     * Deals {@code amount} damage from {@code attacker} to {@code target}
     * through the normal combat pipeline so kill-credit and hitsplats work.
     */
    public static void damage(Player attacker, Mobile target, int amount) {
        if (target == null || target.getHitpoints() <= 0) {
            return;
        }
        if (amount < 0) {
            amount = 0;
        }
        PendingHit hit = PendingHit.create(attacker, target, AbilityCombatMethod.INSTANCE, amount, true);
        CombatFactory.addPendingHit(hit);
    }

    /**
     * Deals damage scaled by the attacker's purchased damage level for
     * {@code ability}.
     */
    public static void damage(Player attacker, Mobile target, int base, Ability ability) {
        damage(attacker, target, scaleDamage(attacker, ability, base));
    }

    /**
     * Queues an immediate, guaranteed hitsplat (used for secondary AoE damage
     * where we don't need the full pipeline).
     */
    public static void splat(Mobile target, int amount) {
        if (target == null || target.getHitpoints() <= 0 || amount <= 0) {
            return;
        }
        target.getCombat().getHitQueue().addPendingDamage(new HitDamage(amount, HitMask.RED));
    }

    /** Queues a guaranteed hitsplat scaled by the caster's damage upgrades. */
    public static void splat(Player caster, Mobile target, int base, Ability ability) {
        splat(target, scaleDamage(caster, ability, base));
    }

    /**
     * Heals {@code caster} and, if it's a player, sends a small confirmation.
     * Used by lifesteal-style abilities.
     */
    public static void lifesteal(Player caster, int amount) {
        if (caster == null || amount <= 0) {
            return;
        }
        caster.heal(amount);
    }

    /** Clears any active freeze/stun on the mobile (anti-crowd-control). */
    public static void cleanse(Mobile target) {
        if (target == null) {
            return;
        }
        target.getTimers().cancel(TimerKey.FREEZE);
        target.getTimers().cancel(TimerKey.STUN);
    }

    /**
     * Pulls every nearby enemy towards {@code caster}, dealing {@code base}
     * damage (scaled) to each. Used by the Gravity Well ability.
     */
    public static void pullEnemies(Player caster, int radius, int base, Ability ability) {
        forEachEnemyNear(caster, caster.getLocation(), radius, 9, m -> {
            Location dest = stepTowards(m.getLocation(), caster.getLocation(), radius, 1, caster.getPrivateArea());
            forceMove(m, dest, 2, 0);
            stun(m, 1);
            damage(caster, m, base, ability);
        });
    }

    /**
     * Stuns a target for the given number of game ticks. We register the timer
     * directly because {@code Misc.getTicks(seconds)} in this base rounds small
     * durations down to zero.
     */
    public static void stun(Mobile target, int ticks) {
        if (target == null) {
            return;
        }
        target.getTimers().register(TimerKey.STUN, ticks);
        target.getCombat().reset();
        target.getMovementQueue().reset();
        target.performGraphic(new Graphic(348, GraphicHeight.HIGH));
        if (target.isPlayer()) {
            target.getAsPlayer().getPacketSender().sendMessage("You've been stunned!");
        }
    }

    public static void anim(Mobile m, int animationId) {
        if (m != null && animationId > 0) {
            m.performAnimation(new Animation(animationId));
        }
    }

    public static void gfx(Mobile m, int graphicId, GraphicHeight height) {
        if (m != null && graphicId > 0) {
            m.performGraphic(new Graphic(graphicId, height));
        }
    }

    /** Sends a tile-based graphic to everyone nearby. */
    public static void tileGfx(int graphicId, Location location, GraphicHeight height) {
        if (graphicId > 0 && location != null) {
            World.sendLocalGraphics(graphicId, location, height);
        }
    }

    // ------------------------------------------------------------------
    // Movement helpers
    // ------------------------------------------------------------------

    /**
     * Steps one tile at a time from {@code from} towards {@code towards},
     * stopping when blocked, when {@code maxSteps} is reached, or when within
     * {@code stopDistance} tiles of the destination. Returns the furthest
     * walkable {@link Location} reached (never {@code null}).
     */
    public static Location stepTowards(Location from, Location towards, int maxSteps, int stopDistance,
                                       PrivateArea area) {
        Location current = from.clone();
        for (int step = 0; step < maxSteps; step++) {
            if (current.getDistance(towards) <= stopDistance) {
                break;
            }
            int dx = Integer.signum(towards.getX() - current.getX());
            int dy = Integer.signum(towards.getY() - current.getY());
            if (dx == 0 && dy == 0) {
                break;
            }
            Location next = current.transform(dx, dy);
            if (!RegionManager.canMove(current, next, 1, 1, area)) {
                break;
            }
            current = next;
        }
        return current;
    }

    /**
     * Slides a target across the ground to {@code destination} using the OSRS
     * forced-movement mask (players) or an instant reposition (NPCs).
     *
     * @param ticks       how many game ticks the slide should take before the
     *                    server repositions the entity
     * @param animationId the animation to play during the slide (0 for none)
     */
    public static void forceMove(Mobile mobile, Location destination, int ticks, int animationId) {
        if (mobile == null || destination == null) {
            return;
        }
        Location start = mobile.getLocation().clone();
        if (start.equals(destination)) {
            return;
        }
        if (mobile.isPlayer()) {
            Location delta = Location.delta(start, destination);
            ForceMovement force = new ForceMovement(start, delta, 0, Math.max(1, ticks * 10), 0, animationId);
            TaskManager.submit(new ForceMovementTask(mobile.getAsPlayer(), ticks, force));
        } else {
            final Mobile m = mobile;
            final Location dest = destination.clone();
            TaskManager.submit(new Task(ticks <= 0 ? 1 : ticks) {
                @Override
                protected void execute() {
                    m.setLocation(dest);
                    m.setResetMovementQueue(true);
                    m.setNeedsPlacement(true);
                    stop();
                }
            });
        }
    }

    /** Object id used as the visual for the temporary Force Wall. */
    private static final int WALL_OBJECT_ID = 4765; // "wall of flame"

    /**
     * Spawns a temporary blocking wall centred on {@code center}, extending
     * {@code halfLength} tiles each way along the ({@code px},{@code py})
     * direction. Each tile is both clipped (blocks movement) and shown as a
     * wall object; both are cleaned up after {@code durationTicks} game ticks.
     *
     * @return the number of wall tiles actually created.
     */
    public static int spawnWall(Player caster, Location center, int px, int py, int halfLength, int durationTicks) {
        final PrivateArea area = caster.getPrivateArea();
        final int z = center.getZ();
        final List<GameObject> created = new ArrayList<>();
        for (int k = -halfLength; k <= halfLength; k++) {
            Location tile = center.transform(px * k, py * k);
            // Don't wall over already-blocked tiles or the caster's own tile.
            if (RegionManager.blocked(tile, area) || tile.equals(caster.getLocation())) {
                continue;
            }
            RegionManager.addClipping(tile.getX(), tile.getY(), z, RegionManager.BLOCKED_TILE, area);
            GameObject obj = new GameObject(WALL_OBJECT_ID, tile, 10, 0, area);
            ObjectManager.register(obj, true);
            created.add(obj);
        }
        if (!created.isEmpty()) {
            delay(durationTicks, () -> {
                for (GameObject obj : created) {
                    RegionManager.removeClipping(obj.getLocation().getX(), obj.getLocation().getY(),
                            obj.getLocation().getZ(), RegionManager.BLOCKED_TILE, area);
                    ObjectManager.deregister(obj, true);
                }
            });
        }
        return created.size();
    }

    /**
     * Fires a straight beam from {@code caster} in the ({@code dx},{@code dy})
     * direction, up to {@code length} tiles (stopping at walls). Shows a tile
     * graphic along the path and deals {@code base} (scaled) damage to every
     * enemy standing in the line.
     *
     * @return the final tile the beam reached (for a visual projectile), or
     *         {@code null} if it couldn't travel at all.
     */
    public static Location fireBeam(Player caster, int dx, int dy, int length, int base, Ability ability,
                                    int tileGfxId, int hitGfxId) {
        final PrivateArea area = caster.getPrivateArea();
        List<Location> beam = new ArrayList<>();
        Location current = caster.getLocation().clone();
        for (int i = 0; i < length; i++) {
            Location next = current.transform(dx, dy);
            if (!RegionManager.canMove(current, next, 1, 1, area)) {
                break;
            }
            current = next;
            beam.add(current.clone());
            tileGfx(tileGfxId, current, GraphicHeight.LOW);
        }
        if (beam.isEmpty()) {
            return null;
        }
        for (Player p : World.getPlayers()) {
            if (p == null || p == caster || !canHit(caster, p)) {
                continue;
            }
            if (beamContains(beam, p.getLocation())) {
                gfx(p, hitGfxId, GraphicHeight.HIGH);
                damage(caster, p, base, ability);
            }
        }
        for (NPC npc : World.getNpcs()) {
            if (npc == null || !canHit(caster, npc)) {
                continue;
            }
            if (beamContains(beam, npc.getLocation())) {
                gfx(npc, hitGfxId, GraphicHeight.HIGH);
                damage(caster, npc, base, ability);
            }
        }
        return beam.get(beam.size() - 1);
    }

    private static boolean beamContains(List<Location> beam, Location loc) {
        for (Location l : beam) {
            if (l.getX() == loc.getX() && l.getY() == loc.getY() && l.getZ() == loc.getZ()) {
                return true;
            }
        }
        return false;
    }

    /** Convenience: schedule {@code action} to run after {@code ticks} game ticks. */
    public static void delay(int ticks, Runnable action) {
        TaskManager.submit(new Task(Math.max(1, ticks)) {
            @Override
            protected void execute() {
                action.run();
                stop();
            }
        });
    }

    /** Returns the maximum hitpoints of a mobile (for scaling effects). */
    public static int maxHitpoints(Mobile m) {
        if (m == null) {
            return 1;
        }
        if (m.isPlayer()) {
            return Math.max(1, m.getAsPlayer().getSkillManager().getMaxLevel(Skill.HITPOINTS));
        }
        if (m.isNpc()) {
            return Math.max(1, m.getAsNpc().getDefinition().getHitpoints());
        }
        return Math.max(1, m.getHitpoints());
    }

    /** Returns the list of all currently online players (snapshot copy). */
    public static List<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (Player p : World.getPlayers()) {
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }
}
