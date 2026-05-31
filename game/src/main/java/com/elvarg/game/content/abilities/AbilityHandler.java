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
        int cooldownSeconds = (int) (cooldownMs / 1000);
        caster.getPacketSender().sendAbilityCooldown(item.getId(), cooldownSeconds);
        caster.getPacketSender().sendMessage("@blu@" + ability.getDisplayName() + " cast! Ready again in "
                + cooldownSeconds + " seconds.");
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
    // Upgrade shop: pricing, donator discount and purchase logic
    // ------------------------------------------------------------------

    /** Cost (in coins) of the player's next cooldown-reduction level. */
    public static long cooldownUpgradeCost(int currentLevel) {
        return 50_000L * (currentLevel + 1);
    }

    /** Cost (in coins) of the player's next damage level. */
    public static long damageUpgradeCost(int currentLevel) {
        return 100_000L * (currentLevel + 1);
    }

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

    /** Attempts to purchase one damage level for {@code ability}. */
    public static void buyDamageUpgrade(Player player, Ability ability) {
        int level = player.getAbilityUpgrades().getDamageLevel(ability.getItemId());
        if (level >= Ability.MAX_DAMAGE_LEVEL) {
            player.getPacketSender().sendMessage(ability.getDisplayName()
                    + "'s damage is already at the maximum (+20%).");
            return;
        }
        long cost = withDonatorDiscount(player, damageUpgradeCost(level));
        if (!spendCoins(player, cost)) {
            return;
        }
        player.getAbilityUpgrades().setDamageLevel(ability.getItemId(), level + 1);
        player.getPacketSender().sendMessage("@blu@" + ability.getDisplayName()
                + " damage upgraded! Now +" + ((level + 1) * 4) + "%.");
    }

    /**
     * Scales a base damage value by the caster's purchased damage level for the
     * given ability.
     */
    public static int scaleDamage(Player caster, Ability ability, int base) {
        if (caster == null || ability == null) {
            return base;
        }
        int level = Math.min(caster.getAbilityUpgrades().getDamageLevel(ability.getItemId()),
                Ability.MAX_DAMAGE_LEVEL);
        if (level <= 0) {
            return base;
        }
        return (int) Math.round(base * (1.0 + 0.04 * level));
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
