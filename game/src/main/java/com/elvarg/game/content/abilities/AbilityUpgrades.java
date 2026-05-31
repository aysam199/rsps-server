package com.elvarg.game.content.abilities;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player progression for the custom ability items.
 * <p>
 * Each ability can be improved in two independent tracks, keyed by the
 * ability's item id (so the data stays valid even if abilities are reordered):
 * <ul>
 *   <li><b>Cooldown</b> - each level shaves 0.25s off the cooldown, capped at
 *       20% of the ability's base cooldown. Universal to every ability.</li>
 *   <li><b>Secondary</b> - a single track whose meaning depends on the ability
 *       (damage, healing, dash distance or freeze duration - see
 *       {@link UpgradeType} and {@link Ability#getSecondaryUpgrade()}).</li>
 * </ul>
 * Stored on the {@link com.elvarg.game.entity.impl.player.Player} and
 * serialised verbatim by the JSON save system.
 *
 * @author Cursor (custom content for RspsApp)
 */
public class AbilityUpgrades {

    /** Maps ability item id -> purchased cooldown-reduction levels. */
    private Map<Integer, Integer> cooldownLevels = new HashMap<>();

    /**
     * Maps ability item id -> purchased secondary-track levels. The field is
     * still named {@code damageLevels} so existing save files keep loading; the
     * value is now the generic secondary level (its meaning depends on the
     * ability's {@link UpgradeType}).
     */
    private Map<Integer, Integer> damageLevels = new HashMap<>();

    /**
     * Maps ability item id -> remaining charges (casts). Abilities are consumable:
     * casting spends a charge, and at zero the item is removed and must be
     * rebought. Upgrade levels (above) are kept separately so they persist across
     * rebuys.
     */
    private Map<Integer, Integer> charges = new HashMap<>();

    public int getCharges(int itemId) {
        return charges == null ? 0 : charges.getOrDefault(itemId, 0);
    }

    public void setCharges(int itemId, int amount) {
        if (charges == null) {
            charges = new HashMap<>();
        }
        charges.put(itemId, Math.max(0, amount));
    }

    public int getCooldownLevel(int itemId) {
        return cooldownLevels == null ? 0 : cooldownLevels.getOrDefault(itemId, 0);
    }

    public void setCooldownLevel(int itemId, int level) {
        if (cooldownLevels == null) {
            cooldownLevels = new HashMap<>();
        }
        cooldownLevels.put(itemId, level);
    }

    /** The ability's secondary-track level (damage/healing/distance/freeze). */
    public int getSecondaryLevel(int itemId) {
        return damageLevels == null ? 0 : damageLevels.getOrDefault(itemId, 0);
    }

    public void setSecondaryLevel(int itemId, int level) {
        if (damageLevels == null) {
            damageLevels = new HashMap<>();
        }
        damageLevels.put(itemId, level);
    }

    /** @deprecated use {@link #getSecondaryLevel(int)}; kept for compatibility. */
    @Deprecated
    public int getDamageLevel(int itemId) {
        return getSecondaryLevel(itemId);
    }

    /** @deprecated use {@link #setSecondaryLevel(int, int)}; kept for compatibility. */
    @Deprecated
    public void setDamageLevel(int itemId, int level) {
        setSecondaryLevel(itemId, level);
    }
}
