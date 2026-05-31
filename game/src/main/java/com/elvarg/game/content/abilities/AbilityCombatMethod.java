package com.elvarg.game.content.abilities;

import com.elvarg.game.content.combat.CombatType;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.game.content.combat.method.CombatMethod;
import com.elvarg.game.entity.impl.Mobile;

/**
 * A lightweight {@link CombatMethod} used so that ability damage flows through
 * the normal combat pipeline (experience, skulling, kill-credit, hitsplats).
 * <p>
 * Damage values for abilities are pre-calculated, so {@link #hits} is never
 * actually invoked - we build the {@link PendingHit} manually via
 * {@link PendingHit#create}. The combat type is reported as MAGIC purely so the
 * engine has a valid type to attribute experience to.
 */
public final class AbilityCombatMethod extends CombatMethod {

    public static final AbilityCombatMethod INSTANCE = new AbilityCombatMethod();

    @Override
    public CombatType type() {
        return CombatType.MAGIC;
    }

    @Override
    public PendingHit[] hits(Mobile character, Mobile target) {
        return new PendingHit[0];
    }

    @Override
    public int attackDistance(Mobile character) {
        // Abilities are ranged; allow generous reach.
        return 12;
    }
}
