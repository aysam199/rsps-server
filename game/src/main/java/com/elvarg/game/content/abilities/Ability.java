package com.elvarg.game.content.abilities;

import java.util.HashMap;
import java.util.Map;

import com.elvarg.game.content.combat.CombatFactory;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.GraphicHeight;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.Projectile;
import com.elvarg.util.Misc;

/**
 * Defines the custom MOBA-style ability items.
 * <p>
 * Each constant binds an in-game item id to an ability with its own cooldown,
 * targeting style and effect. Visual ids (animations / graphics / projectiles)
 * use common values and are easy to tune.
 *
 * @author Cursor (custom content for RspsApp)
 */
public enum Ability {

    /**
     * Blitzcrank-style hook. Fires a projectile at an enemy, then violently
     * drags them across the ground to your feet, stunning and damaging them.
     */
    HOOK(12006, "Hook", 8_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You launch your hook!");
            AbilityHandler.anim(caster, 1658); // whip-style swing
            Projectile.sendProjectile(caster, target,
                    new Projectile(PROJECTILE_HOOK, 40, 36, 35, 10));

            final Location casterTile = caster.getLocation().clone();
            AbilityHandler.delay(2, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                Location dest = AbilityHandler.stepTowards(target.getLocation(), casterTile, 12, 1,
                        caster.getPrivateArea());
                AbilityHandler.forceMove(target, dest, 2, 0);
                AbilityHandler.stun(target, 3);
                AbilityHandler.damage(caster, target, 4 + Misc.getRandom(8), this);
                if (target.isPlayer()) {
                    target.getAsPlayer().getPacketSender().sendMessage("You've been hooked!");
                }
            });
        }
    },

    /**
     * A quick gap-closer. Dashes you several tiles towards the nearest enemy
     * (or forwards if none are near).
     */
    DASH(13237, "Dash", 6_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            Mobile enemy = AbilityHandler.nearestEnemy(caster, 8);
            Location towards = (enemy != null)
                    ? enemy.getLocation()
                    : caster.getLocation().transform(0, 4); // default: forwards (north)
            Location dest = AbilityHandler.stepTowards(caster.getLocation(), towards, 4,
                    enemy != null ? 1 : 0, caster.getPrivateArea());
            AbilityHandler.gfx(caster, GFX_DASH, GraphicHeight.LOW);
            AbilityHandler.forceMove(caster, dest, 1, 0);
            caster.getPacketSender().sendMessage("You dash forward!");
        }
    },

    /**
     * Slams the ground, damaging and stunning every enemy immediately around
     * you.
     */
    EARTHQUAKE_STOMP(13576, "Earthquake Stomp", 12_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You slam the ground with tremendous force!");
            AbilityHandler.anim(caster, 1378); // dragon warhammer slam
            AbilityHandler.tileGfx(GFX_EARTHQUAKE, caster.getLocation(), GraphicHeight.LOW);
            AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 1, 8, m -> {
                AbilityHandler.damage(caster, m, 8 + Misc.getRandom(12), this);
                AbilityHandler.stun(m, 2);
            });
        }
    },

    /**
     * A bolt that strikes an enemy and arcs to up to two further enemies nearby.
     */
    CHAIN_LIGHTNING(11907, "Chain Lightning", 9_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("Lightning crackles from your trident!");
            AbilityHandler.anim(caster, 1979);
            Projectile.sendProjectile(caster, target, new Projectile(PROJECTILE_CHAIN, 43, 31, 35, 10));
            AbilityHandler.delay(2, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                AbilityHandler.gfx(target, GFX_CHAIN_HIT, GraphicHeight.HIGH);
                AbilityHandler.damage(caster, target, 10 + Misc.getRandom(12), this);

                final int[] arcs = {0};
                AbilityHandler.forEachEnemyNear(caster, target.getLocation(), 3, 8, m -> {
                    if (m == target || arcs[0] >= 2) {
                        return;
                    }
                    arcs[0]++;
                    Projectile.sendProjectile(target, m, new Projectile(PROJECTILE_CHAIN, 43, 31, 10, 10));
                    AbilityHandler.gfx(m, GFX_CHAIN_HIT, GraphicHeight.HIGH);
                    AbilityHandler.splat(caster, m, 5 + Misc.getRandom(7), this);
                });
            });
        }
    },

    /**
     * Bursts ice outwards, freezing (and lightly damaging) all nearby enemies.
     */
    FROST_NOVA(4675, "Frost Nova", 15_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You unleash a freezing nova!");
            AbilityHandler.anim(caster, 1979);
            AbilityHandler.tileGfx(GFX_FROST, caster.getLocation(), GraphicHeight.LOW);
            AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 2, 8, m -> {
                AbilityHandler.gfx(m, GFX_FROST, GraphicHeight.HIGH);
                CombatFactory.freeze(m, 8);
                AbilityHandler.splat(caster, m, 2 + Misc.getRandom(6), this);
            });
        }
    },

    /**
     * Calls down a delayed meteor at an enemy's location, devastating the area.
     */
    METEOR_STRIKE(20714, "Meteor Strike", 18_000, 12, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You call down a meteor!");
            AbilityHandler.anim(caster, 1979);
            final Location impact = target.getLocation().clone();
            Projectile.sendProjectile(caster.getLocation(), impact, new Projectile(PROJECTILE_METEOR, 90, 36, 40, 12));
            AbilityHandler.delay(3, () -> {
                AbilityHandler.tileGfx(GFX_METEOR_IMPACT, impact, GraphicHeight.HIGH);
                AbilityHandler.forEachEnemyNear(caster, impact, 2, 9, m -> {
                    int distance = m.getLocation().getDistance(impact);
                    int dmg = (distance == 0 ? 18 : 12) + Misc.getRandom(distance == 0 ? 17 : 10);
                    AbilityHandler.damage(caster, m, dmg, this);
                });
            });
        }
    },

    /**
     * Leap onto a target, slamming down for area damage and a brief stun.
     */
    HEROIC_LEAP(13652, "Heroic Leap", 12_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You leap towards your foe!");
            AbilityHandler.anim(caster, 7514); // dragon claws spec
            Location landing = AbilityHandler.stepTowards(caster.getLocation(), target.getLocation(), 8, 1,
                    caster.getPrivateArea());
            AbilityHandler.forceMove(caster, landing, 2, 0);
            AbilityHandler.delay(3, () -> {
                AbilityHandler.tileGfx(GFX_LEAP_IMPACT, caster.getLocation(), GraphicHeight.LOW);
                AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 1, 8, m -> {
                    AbilityHandler.damage(caster, m, 8 + Misc.getRandom(10), this);
                    AbilityHandler.stun(m, 2);
                });
            });
        }
    },

    /**
     * Heals you steadily over a few seconds.
     */
    REJUVENATE(11806, "Rejuvenate", 20_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("A soothing energy washes over you.");
            AbilityHandler.anim(caster, 1979);
            for (int tick = 1; tick <= 4; tick++) {
                AbilityHandler.delay(tick, () -> {
                    if (caster.getHitpoints() > 0) {
                        caster.heal(6);
                        AbilityHandler.gfx(caster, GFX_REJUVENATE, GraphicHeight.LOW);
                    }
                });
            }
        }
    },

    /**
     * Drops a smoke screen: freezes nearby enemies and blinks you backwards to
     * safety.
     */
    SMOKE_BOMB(11998, "Smoke Bomb", 14_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You vanish in a cloud of smoke!");
            AbilityHandler.tileGfx(GFX_SMOKE, caster.getLocation(), GraphicHeight.LOW);
            AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 1, 8, m -> CombatFactory.freeze(m, 4));

            Mobile enemy = AbilityHandler.nearestEnemy(caster, 8);
            Location away;
            if (enemy != null) {
                // Move directly away from the nearest enemy.
                int dx = Integer.signum(caster.getLocation().getX() - enemy.getLocation().getX());
                int dy = Integer.signum(caster.getLocation().getY() - enemy.getLocation().getY());
                if (dx == 0 && dy == 0) {
                    dy = 1;
                }
                away = caster.getLocation().transform(dx * 3, dy * 3);
            } else {
                away = caster.getLocation().transform(0, -3);
            }
            Location dest = AbilityHandler.stepTowards(caster.getLocation(), away, 3, 0, caster.getPrivateArea());
            AbilityHandler.forceMove(caster, dest, 1, 0);
        }
    },

    /**
     * A finisher whose damage scales with how much health the target is
     * missing - lethal against wounded foes.
     */
    EXECUTE(11802, "Execute", 10_000, 8, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            AbilityHandler.anim(caster, 7642); // armadyl godsword spec
            AbilityHandler.gfx(target, GFX_EXECUTE, GraphicHeight.HIGH);

            int max = AbilityHandler.maxHitpoints(target);
            int current = target.getHitpoints();
            double missingFraction = Math.max(0, Math.min(1.0, (max - current) / (double) max));
            // Base 6-12, plus up to +30 based on missing health.
            int dmg = AbilityHandler.scaleDamage(caster, this, 6 + Misc.getRandom(6) + (int) (missingFraction * 30));
            AbilityHandler.damage(caster, target, dmg);
            caster.getPacketSender().sendMessage("Execute strikes for " + dmg + " damage!");
        }
    },

    /**
     * Drains an enemy's life force at range, damaging them and healing you for
     * half of the damage dealt.
     */
    SOUL_DRAIN(22323, "Soul Drain", 11_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You siphon your foe's life force!");
            AbilityHandler.anim(caster, 1979);
            Projectile.sendProjectile(caster, target, new Projectile(PROJECTILE_SOUL, 43, 31, 35, 10));
            AbilityHandler.delay(2, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                AbilityHandler.gfx(target, GFX_SOUL_HIT, GraphicHeight.HIGH);
                int dmg = AbilityHandler.scaleDamage(caster, this, 8 + Misc.getRandom(12));
                AbilityHandler.damage(caster, target, dmg);
                AbilityHandler.lifesteal(caster, dmg / 2);
                AbilityHandler.gfx(caster, GFX_REJUVENATE, GraphicHeight.LOW);
            });
        }
    },

    /**
     * Roots a single target in place at range with a brief freeze and light
     * damage - great for stopping a runner.
     */
    ENTANGLE(21006, "Entangle", 9_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("Vines erupt around your target!");
            AbilityHandler.anim(caster, 1979);
            Projectile.sendProjectile(caster, target, new Projectile(PROJECTILE_ENTANGLE, 43, 31, 35, 10));
            AbilityHandler.delay(2, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                AbilityHandler.gfx(target, GFX_ENTANGLE, GraphicHeight.LOW);
                CombatFactory.freeze(target, 10);
                AbilityHandler.damage(caster, target, 3 + Misc.getRandom(6), this);
                if (target.isPlayer()) {
                    target.getAsPlayer().getPacketSender().sendMessage("You're held in place by vines!");
                }
            });
        }
    },

    /**
     * Spin in a deadly whirlwind, striking everything around you twice over a
     * couple of game ticks.
     */
    WHIRLWIND(23987, "Whirlwind", 13_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You spin into a furious whirlwind!");
            AbilityHandler.anim(caster, 1067); // halberd-style swing
            for (int wave = 1; wave <= 2; wave++) {
                AbilityHandler.delay(wave, () -> {
                    AbilityHandler.tileGfx(GFX_EARTHQUAKE, caster.getLocation(), GraphicHeight.LOW);
                    AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 1, 9,
                            m -> AbilityHandler.damage(caster, m, 5 + Misc.getRandom(8), this));
                });
            }
        }
    },

    /**
     * Collapses a gravity well at your feet, dragging in every nearby enemy and
     * stunning them briefly.
     */
    GRAVITY_WELL(12904, "Gravity Well", 16_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You collapse a gravity well!");
            AbilityHandler.anim(caster, 1979);
            AbilityHandler.tileGfx(GFX_FROST, caster.getLocation(), GraphicHeight.LOW);
            AbilityHandler.pullEnemies(caster, 5, 4 + Misc.getRandom(8), this);
        }
    },

    /**
     * Blink to your target and strike, briefly stunning them. A pure gap-closer
     * finisher.
     */
    BLINK_STRIKE(13899, "Blink Strike", 9_000, 10, true) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You blink to your foe!");
            AbilityHandler.gfx(caster, GFX_DASH, GraphicHeight.LOW);
            Location landing = AbilityHandler.stepTowards(caster.getLocation(), target.getLocation(), 12, 1,
                    caster.getPrivateArea());
            AbilityHandler.forceMove(caster, landing, 1, 0);
            AbilityHandler.delay(1, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                AbilityHandler.anim(caster, 7642);
                AbilityHandler.gfx(target, GFX_EXECUTE, GraphicHeight.HIGH);
                AbilityHandler.damage(caster, target, 8 + Misc.getRandom(12), this);
                AbilityHandler.stun(target, 2);
            });
        }
    },

    /**
     * Raise a protective bulwark: instantly cleanse any freeze/stun on yourself
     * and recover a burst of health.
     */
    BULWARK(21015, "Bulwark", 18_000, 0, false) {
        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You raise an unbreakable bulwark!");
            AbilityHandler.anim(caster, 4177);
            AbilityHandler.gfx(caster, GFX_REJUVENATE, GraphicHeight.HIGH);
            AbilityHandler.cleanse(caster);
            caster.heal(15);
            for (int tick = 2; tick <= 4; tick += 2) {
                AbilityHandler.delay(tick, () -> {
                    if (caster.getHitpoints() > 0) {
                        caster.heal(5);
                        AbilityHandler.gfx(caster, GFX_REJUVENATE, GraphicHeight.LOW);
                    }
                });
            }
        }
    };

    // ------------------------------------------------------------------
    // Visual ids (tunable)
    // ------------------------------------------------------------------
    private static final int PROJECTILE_HOOK = 376;     // blood-barrage style bolt
    private static final int PROJECTILE_CHAIN = 384;    // shadow-barrage style bolt
    private static final int PROJECTILE_METEOR = 368;   // ice-barrage style bolt
    private static final int PROJECTILE_SOUL = 376;     // soul-drain bolt
    private static final int PROJECTILE_ENTANGLE = 178; // entangle bolt

    private static final int GFX_DASH = 268;
    private static final int GFX_EARTHQUAKE = 369;
    private static final int GFX_CHAIN_HIT = 385;
    private static final int GFX_FROST = 369;
    private static final int GFX_METEOR_IMPACT = 369;
    private static final int GFX_LEAP_IMPACT = 369;
    private static final int GFX_REJUVENATE = 436;
    private static final int GFX_SMOKE = 385;
    private static final int GFX_EXECUTE = 1224;
    private static final int GFX_SOUL_HIT = 377;
    private static final int GFX_ENTANGLE = 179;

    // ------------------------------------------------------------------
    private final int itemId;
    private final String displayName;
    private final long cooldownMs;
    private final int range;
    private final boolean targetsEnemy;

    Ability(int itemId, String displayName, long cooldownMs, int range, boolean targetsEnemy) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.cooldownMs = cooldownMs;
        this.range = range;
        this.targetsEnemy = targetsEnemy;
    }

    /**
     * Performs the ability's effect.
     *
     * @param caster the casting player
     * @param target the target (the caster itself for self-cast abilities)
     */
    public abstract void activate(Player caster, Mobile target);

    public int getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    /** Maximum cast range in tiles (only meaningful for enemy-targeted abilities). */
    public int getRange() {
        return range;
    }

    public boolean targetsEnemy() {
        return targetsEnemy;
    }

    // ------------------------------------------------------------------
    // Upgrade economy
    // ------------------------------------------------------------------

    /** Each cooldown level removes this many milliseconds. */
    public static final int COOLDOWN_STEP_MS = 250;

    /** Maximum total cooldown reduction, as a fraction of the base cooldown. */
    public static final double MAX_COOLDOWN_REDUCTION = 0.20;

    /** Maximum number of damage levels (each +4% => +20% total). */
    public static final int MAX_DAMAGE_LEVEL = 5;

    /** Highest cooldown level a player may buy for this ability. */
    public int getMaxCooldownLevel() {
        return (int) (cooldownMs * MAX_COOLDOWN_REDUCTION / COOLDOWN_STEP_MS);
    }

    /** The cooldown this player experiences after their purchased reductions. */
    public long effectiveCooldownMs(Player player) {
        int level = Math.min(player.getAbilityUpgrades().getCooldownLevel(itemId), getMaxCooldownLevel());
        return cooldownMs - (long) COOLDOWN_STEP_MS * level;
    }

    /** Price (in coins) to buy this ability from the Ability Emporium. */
    public long getBuyCost() {
        return 100_000L + cooldownMs * 25L;
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------
    private static final Map<Integer, Ability> BY_ITEM = new HashMap<>();

    static {
        for (Ability ability : values()) {
            BY_ITEM.put(ability.itemId, ability);
        }
    }

    public static Ability forItem(int itemId) {
        return BY_ITEM.get(itemId);
    }
}
