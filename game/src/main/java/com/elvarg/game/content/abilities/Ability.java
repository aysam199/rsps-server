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
            // Dash is ground-targeted (see activateAt). This fallback only runs if
            // it's ever cast without a tile click - dash straight ahead (north).
            activateAt(caster, caster.getLocation().transform(0, DASH_TILES));
        }

        @Override
        public boolean isGroundTargeted() {
            return true;
        }

        @Override
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.DISTANCE;
        }

        @Override
        public boolean activateAt(Player caster, Location clicked) {
            Location from = caster.getLocation();
            int dx = Integer.signum(clicked.getX() - from.getX());
            int dy = Integer.signum(clicked.getY() - from.getY());
            if (dx == 0 && dy == 0) {
                caster.getPacketSender().sendMessage("Click a tile away from yourself to dash.");
                return false;
            }
            // Dash a fixed number of tiles in the clicked direction (plus any
            // purchased Distance upgrades), stopping early only if blocked.
            int tiles = DASH_TILES + AbilityHandler.bonusDistance(caster, this);
            Location aim = from.transform(dx * tiles, dy * tiles);
            Location dest = AbilityHandler.stepTowards(from, aim, tiles, 0, caster.getPrivateArea());
            AbilityHandler.gfx(caster, GFX_DASH, GraphicHeight.LOW);
            AbilityHandler.forceMove(caster, dest, 1, 0);
            caster.getPacketSender().sendMessage("You dash!");
            return true;
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
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.FREEZE;
        }

        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You unleash a freezing nova!");
            AbilityHandler.anim(caster, 1979);
            AbilityHandler.tileGfx(GFX_FROST, caster.getLocation(), GraphicHeight.LOW);
            int freeze = 8 + AbilityHandler.freezeBonus(caster, this);
            AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 2, 8, m -> {
                AbilityHandler.gfx(m, GFX_FROST, GraphicHeight.HIGH);
                CombatFactory.freeze(m, freeze);
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
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.HEALING;
        }

        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("A soothing energy washes over you.");
            AbilityHandler.anim(caster, 1979);
            int healPerTick = AbilityHandler.scaleHeal(caster, this, 6);
            for (int tick = 1; tick <= 4; tick++) {
                AbilityHandler.delay(tick, () -> {
                    if (caster.getHitpoints() > 0) {
                        caster.heal(healPerTick);
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
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.FREEZE;
        }

        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You vanish in a cloud of smoke!");
            AbilityHandler.tileGfx(GFX_SMOKE, caster.getLocation(), GraphicHeight.LOW);
            int freeze = 4 + AbilityHandler.freezeBonus(caster, this);
            AbilityHandler.forEachEnemyNear(caster, caster.getLocation(), 1, 8, m -> CombatFactory.freeze(m, freeze));

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
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.FREEZE;
        }

        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("Vines erupt around your target!");
            AbilityHandler.anim(caster, 1979);
            Projectile.sendProjectile(caster, target, new Projectile(PROJECTILE_ENTANGLE, 43, 31, 35, 10));
            int freeze = 10 + AbilityHandler.freezeBonus(caster, this);
            AbilityHandler.delay(2, () -> {
                if (!AbilityHandler.canHit(caster, target)) {
                    return;
                }
                AbilityHandler.gfx(target, GFX_ENTANGLE, GraphicHeight.LOW);
                CombatFactory.freeze(target, freeze);
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
        public UpgradeType getSecondaryUpgrade() {
            return UpgradeType.HEALING;
        }

        @Override
        public void activate(Player caster, Mobile target) {
            caster.getPacketSender().sendMessage("You raise an unbreakable bulwark!");
            AbilityHandler.anim(caster, 4177);
            AbilityHandler.gfx(caster, GFX_REJUVENATE, GraphicHeight.HIGH);
            AbilityHandler.cleanse(caster);
            caster.heal(AbilityHandler.scaleHeal(caster, this, 15));
            int healPerTick = AbilityHandler.scaleHeal(caster, this, 5);
            for (int tick = 2; tick <= 4; tick += 2) {
                AbilityHandler.delay(tick, () -> {
                    if (caster.getHitpoints() > 0) {
                        caster.heal(healPerTick);
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

    /** Number of tiles the Dash ability travels. */
    private static final int DASH_TILES = 4;

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

    /**
     * Whether this ability waits for the player to click a destination tile
     * after activation (e.g. Dash) instead of firing immediately.
     */
    public boolean isGroundTargeted() {
        return false;
    }

    /**
     * Performs a ground-targeted ability at the clicked {@code target} tile.
     *
     * @return {@code true} if the ability fired (so its cooldown should start),
     *         or {@code false} to ask the player to click a different tile.
     */
    public boolean activateAt(Player caster, Location target) {
        return false;
    }

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

    /**
     * The ability's secondary upgrade track (every ability also has Cooldown).
     * Defaults to {@link UpgradeType#DAMAGE}; abilities that deal no damage
     * override this with a track that fits them (distance, healing, freeze).
     */
    public UpgradeType getSecondaryUpgrade() {
        return UpgradeType.DAMAGE;
    }

    /** Highest secondary-track level a player may buy for this ability. */
    public int getMaxSecondaryLevel() {
        return getSecondaryUpgrade().getMaxLevel();
    }

    /** Highest cooldown level a player may buy for this ability. */
    public int getMaxCooldownLevel() {
        return (int) (cooldownMs * MAX_COOLDOWN_REDUCTION / COOLDOWN_STEP_MS);
    }

    /** The cooldown this player experiences after their purchased reductions. */
    public long effectiveCooldownMs(Player player) {
        int level = Math.min(player.getAbilityUpgrades().getCooldownLevel(itemId), getMaxCooldownLevel());
        return cooldownMs - (long) COOLDOWN_STEP_MS * level;
    }

    /**
     * Price (in coins) to buy one pack of charges of this ability from the
     * Ability Emporium (see {@link AbilityHandler#CHARGES_PER_PURCHASE}).
     * Abilities are consumable, so this is a recurring cost - priced as a gold
     * sink that scales with the ability's cooldown/power.
     */
    public long getBuyCost() {
        return 50_000L + cooldownMs * 15L;
    }

    // ------------------------------------------------------------------
    // Descriptions (shown on examine, in shops and inventory)
    // ------------------------------------------------------------------
    private static final Map<Integer, String> DESCRIPTIONS = new HashMap<>() {{
        put(12006, "Hook: fire a projectile that drags an enemy to your feet, stunning and damaging them.");
        put(13237, "Dash: activate, then click a tile to dash 4 tiles in that direction (upgradeable distance).");
        put(13576, "Earthquake Stomp: slam the ground, damaging and stunning every enemy around you.");
        put(11907, "Chain Lightning: strike a target and arc to up to 2 more nearby enemies.");
        put(4675, "Frost Nova: freeze and lightly damage all enemies around you (upgradeable freeze).");
        put(20714, "Meteor Strike: call down a delayed meteor on a target's area for heavy damage.");
        put(13652, "Heroic Leap: leap to a foe, dealing area damage and a brief stun on landing.");
        put(11806, "Rejuvenate: heal yourself steadily over a few seconds (upgradeable healing).");
        put(11998, "Smoke Bomb: freeze nearby enemies and blink yourself to safety (upgradeable freeze).");
        put(11802, "Execute: a finisher whose damage scales with the target's missing health.");
        put(22323, "Soul Drain: damage a foe at range and heal yourself for half the damage dealt.");
        put(21006, "Entangle: root a single target in place with a freeze and light damage (upgradeable freeze).");
        put(23987, "Whirlwind: spin and strike everything around you twice.");
        put(12904, "Gravity Well: drag in every nearby enemy and stun them.");
        put(13899, "Blink Strike: blink to a target, striking and briefly stunning them.");
        put(21015, "Bulwark: cleanse freezes/stuns on yourself and recover a burst of health (upgradeable healing).");
    }};

    /**
     * A player-facing description of what this ability does, including its
     * base cooldown. Shown when the ability item is examined.
     */
    public String getDescription() {
        String base = DESCRIPTIONS.getOrDefault(itemId, displayName + ": a custom ability.");
        return base + " (" + (cooldownMs / 1000) + "s cooldown)";
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
