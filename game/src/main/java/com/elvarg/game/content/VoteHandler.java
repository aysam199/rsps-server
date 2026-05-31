package com.elvarg.game.content;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.util.Misc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Voting rewards. Players vote for the server on a toplist site (which boosts
 * the server's ranking and visibility) and, in return, receive in-game coins.
 *
 * <p>The {@code ::vote} command opens this. It always shows players the vote
 * link, and - if a toplist vote-check API is configured below - it verifies the
 * vote over HTTP (off the game thread) and credits the reward.</p>
 *
 * <p><b>To enable rewards:</b> register the server on a toplist, then fill in
 * {@link #VOTE_SITE_URL} and {@link #VOTE_CHECK_URL} below. Until then, voting
 * still works as a link; rewards simply aren't auto-credited.</p>
 *
 * @author Custom
 */
public final class VoteHandler {

    private static final int COINS = 995;

    // =====================================================================
    //  CONFIGURE THESE once you register the server on a toplist website.
    // =====================================================================

    /** The page players open in their browser to vote. Leave blank to disable. */
    public static final String VOTE_SITE_URL = "";

    /**
     * The toplist's vote-check API endpoint. Supports {user} and {ip}
     * placeholders, which are filled in per player. Leave blank to disable
     * automatic verification (the vote link is still shown).
     *
     * Many toplists return a tiny body where "1" means the player has an
     * unclaimed vote. If your toplist differs, edit {@link #isVoteClaimable}.
     *
     * Example: "https://www.example-toplist.com/api/?key=YOURKEY&action=check&user={user}&ip={ip}"
     */
    public static final String VOTE_CHECK_URL = "";

    /** Coins granted per confirmed vote. */
    public static final int COINS_PER_VOTE = 30_000;

    private VoteHandler() {
    }

    /**
     * Entry point for the {@code ::vote} command.
     */
    public static void vote(Player player) {
        if (isBlank(VOTE_SITE_URL)) {
            player.getPacketSender().sendMessage("Voting isn't set up yet - check back soon!");
            return;
        }

        player.getPacketSender().sendMessage("@blu@Vote for us here: " + VOTE_SITE_URL);
        player.getPacketSender().sendMessage("You'll receive "
                + Misc.insertCommasToNumber(Integer.toString(COINS_PER_VOTE)) + " coins for each vote.");

        if (isBlank(VOTE_CHECK_URL)) {
            player.getPacketSender().sendMessage("Your reward will be credited automatically once voting is fully enabled.");
            return;
        }

        player.getPacketSender().sendMessage("Checking your vote status...");

        final String username = player.getUsername();
        final String ip = player.getHostAddress() == null ? "" : player.getHostAddress();

        // Perform the network call off the game thread so the server never blocks.
        Thread worker = new Thread(() -> {
            boolean claimable = false;
            try {
                String url = VOTE_CHECK_URL
                        .replace("{user}", URLEncoder.encode(username, "UTF-8"))
                        .replace("{ip}", URLEncoder.encode(ip, "UTF-8"));
                claimable = isVoteClaimable(httpGet(url));
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (claimable) {
                // Marshal the actual coin grant onto the game thread.
                player.addPendingVoteRewards(1);
            } else {
                // Netty channel writes are thread-safe, so messaging here is fine.
                player.getPacketSender().sendMessage("We couldn't find a new vote from you yet. "
                        + "Vote using the link, then type ::vote again.");
            }
        }, "VoteCheck-" + username);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Grants any votes confirmed off-thread. Called from {@code Player#process}
     * on the game thread, so container mutation here is safe.
     */
    public static void grantPendingRewards(Player player) {
        int votes = player.getPendingVoteRewards().getAndSet(0);
        if (votes <= 0) {
            return;
        }

        int reward = votes * COINS_PER_VOTE;
        player.setTotalVotes(player.getTotalVotes() + votes);
        DailyRewards.grantCoins(player, reward);
        player.getPacketSender().sendMessage("@gre@Thanks for voting! You received "
                + Misc.insertCommasToNumber(Integer.toString(reward)) + " coins. Total votes: "
                + player.getTotalVotes() + ".");
    }

    /**
     * Interprets the toplist's vote-check response. Default convention: a body
     * of "1" means there's an unclaimed vote. Edit to match your toplist.
     */
    private static boolean isVoteClaimable(String response) {
        return response != null && response.trim().equals("1");
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Elvarg-RSPS");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
