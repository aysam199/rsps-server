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
 *       20% of the ability's base cooldown.</li>
 *   <li><b>Damage</b> - each level adds +4% damage, capped at +20%.</li>
 * </ul>
 * Stored on the {@link com.elvarg.game.entity.impl.player.Player} and
 * serialised verbatim by the JSON save system.
 *
 * @author Cursor (custom content for RspsApp)
 */
public class AbilityUpgrades {

    /** Maps ability item id -> purchased cooldown-reduction levels. */
    private Map<Integer, Integer> cooldownLevels = new HashMap<>();

    /** Maps ability item id -> purchased damage levels. */
    private Map<Integer, Integer> damageLevels = new HashMap<>();

    public int getCooldownLevel(int itemId) {
        return cooldownLevels == null ? 0 : cooldownLevels.getOrDefault(itemId, 0);
    }

    public int getDamageLevel(int itemId) {
        return damageLevels == null ? 0 : damageLevels.getOrDefault(itemId, 0);
    }

    public void setCooldownLevel(int itemId, int level) {
        if (cooldownLevels == null) {
            cooldownLevels = new HashMap<>();
        }
        cooldownLevels.put(itemId, level);
    }

    public void setDamageLevel(int itemId, int level) {
        if (damageLevels == null) {
            damageLevels = new HashMap<>();
        }
        damageLevels.put(itemId, level);
    }
}
