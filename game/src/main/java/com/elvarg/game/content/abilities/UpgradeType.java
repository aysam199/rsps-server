package com.elvarg.game.content.abilities;

/**
 * The kinds of "secondary" upgrade an ability can offer (every ability also has
 * the universal Cooldown track). Each ability declares exactly one secondary
 * track that fits its identity, so players never see a useless upgrade (e.g. a
 * Damage upgrade on a movement or healing ability).
 *
 * @author Custom
 */
public enum UpgradeType {

    /** +4% damage per level, up to +20% (5 levels). For damage-dealing abilities. */
    DAMAGE("Damage", 5, "+4%", "+20%"),

    /** +4% healing per level, up to +20% (5 levels). For healing abilities. */
    HEALING("Healing", 5, "+4%", "+20%"),

    /** +1 tile per level, up to +2 tiles. For movement abilities (Dash). */
    DISTANCE("Distance", 2, "+1 tile", "+2 tiles"),

    /** +1 tick of freeze/root per level, up to +4. For crowd-control abilities. */
    FREEZE("Freeze", 4, "+1 tick", "+4 ticks");

    private final String displayName;
    private final int maxLevel;
    private final String stepLabel;
    private final String maxLabel;

    UpgradeType(String displayName, int maxLevel, String stepLabel, String maxLabel) {
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.stepLabel = stepLabel;
        this.maxLabel = maxLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** The per-level effect, e.g. "+4%" or "+1 tile". */
    public String getStepLabel() {
        return stepLabel;
    }

    /** The total effect once fully upgraded, e.g. "+20%" or "+2 tiles". */
    public String getMaxLabel() {
        return maxLabel;
    }
}
