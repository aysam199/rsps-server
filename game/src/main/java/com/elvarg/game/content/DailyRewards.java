package com.elvarg.game.content;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.util.Misc;

/**
 * Handles the daily login reward. Players who log in get a coin reward once per
 * day. Logging in on consecutive days builds a streak that increases the reward
 * up to a cap, encouraging players to return without letting them stockpile
 * wealth quickly (the daily amounts are deliberately small relative to ability
 * costs).
 *
 * @author Custom
 */
public final class DailyRewards {

    /** Item id for coins. */
    private static final int COINS = 995;

    /** Minimum time between claims (20 hours), so it's effectively "once a day". */
    private static final long CLAIM_INTERVAL_MS = 20L * 60L * 60L * 1000L;

    /** If a player returns within this window, their streak continues; otherwise it resets. */
    private static final long STREAK_GRACE_MS = 44L * 60L * 60L * 1000L;

    /** Base reward on day 1 of a streak. */
    private static final int BASE_REWARD = 20_000;

    /** Extra coins added per consecutive day. */
    private static final int PER_DAY_BONUS = 5_000;

    /** Streak day at which the reward stops growing (day 7 => 50k). */
    private static final int MAX_STREAK = 7;

    private DailyRewards() {
    }

    public static void handleLogin(Player player) {
        long now = System.currentTimeMillis();
        long last = player.getLastDailyReward();
        long sinceLast = now - last;

        // Not yet eligible for today's reward.
        if (last != 0 && sinceLast < CLAIM_INTERVAL_MS) {
            return;
        }

        // Continue the streak if they returned in time, otherwise start over.
        int streak = (last != 0 && sinceLast <= STREAK_GRACE_MS) ? player.getDailyStreak() + 1 : 1;
        if (streak > MAX_STREAK) {
            streak = MAX_STREAK;
        }

        int reward = BASE_REWARD + (streak - 1) * PER_DAY_BONUS;

        player.setDailyStreak(streak);
        player.setLastDailyReward(now);

        grantCoins(player, reward);

        player.getPacketSender().sendMessage("@gre@Daily reward: " + Misc.insertCommasToNumber(Integer.toString(reward))
                + " coins! (Day " + streak + " streak)");
        if (streak < MAX_STREAK) {
            player.getPacketSender().sendMessage("Log in again tomorrow to grow your streak and reward.");
        }
    }

    /**
     * Adds coins to the player's inventory, falling back to the bank if the
     * inventory cannot hold them.
     */
    static void grantCoins(Player player, int amount) {
        if (player.getInventory().getFreeSlots() > 0 || player.getInventory().contains(COINS)) {
            player.getInventory().add(new Item(COINS, amount));
        } else {
            player.getBank(0).add(new Item(COINS, amount));
            player.getPacketSender().sendMessage("Your inventory was full, so the coins went to your bank.");
        }
    }
}
