package com.elvarg.game.content;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.util.ItemIdentifiers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Voting rewards. Players vote for the server on one or more toplist sites
 * (which boosts the server's ranking and visibility) and, in return, receive
 * <b>Vote tickets</b> that they spend in the {@link VoteShop}.
 *
 * <p>The server can be listed on several toplists at once - see {@link #SITES}.
 * A player can vote once per site per ~12h, so listing on three sites lets a
 * dedicated voter earn up to three tickets a day, all credited to the same
 * account.</p>
 *
 * <p>The {@code ::vote} command shows every vote link and, for each site that
 * has a verify URL configured, checks the vote over HTTP (off the game thread)
 * and credits a ticket. A per-site 12h cooldown is stored on the account so a
 * player can't re-claim the same vote by spamming {@code ::vote} (or relogging),
 * regardless of what the toplist API reports.</p>
 *
 * <p><b>To enable rewards:</b> register on a toplist, then add an entry to
 * {@link #SITES} with the vote-page URL and the verify/callback URL (with your
 * key). Until then, voting still works as links; rewards just aren't credited.</p>
 *
 * @author Custom
 */
public final class VoteHandler {

    /** A single toplist the server is listed on. */
    public static final class Toplist {
        /** Display name; also the key used for the per-site cooldown. Keep it unique. */
        public final String name;
        /** The page players open in their browser to vote. */
        public final String voteUrl;
        /**
         * The toplist's verify/callback API. Supports {user} and {ip}
         * placeholders, filled in per player. Leave blank to just show the link
         * without auto-rewarding. Many toplists return a body of "1" when the
         * player has an unclaimed vote - see {@link #isVoteClaimable}.
         */
        public final String checkUrl;

        public Toplist(String name, String voteUrl, String checkUrl) {
            this.name = name;
            this.voteUrl = voteUrl;
            this.checkUrl = checkUrl;
        }
    }

    // =====================================================================
    //  CONFIGURE THESE once you register the server on toplist websites.
    //  Add one entry per site. Example:
    //
    //    new Toplist("RuneLocus",
    //        "https://www.runelocus.com/vote/?id=YOURID",
    //        "https://www.runelocus.com/modules/vote/check.php?key=KEY&user={user}&ip={ip}"),
    //    new Toplist("RuneList",
    //        "https://runelist.io/vote/YOURID",
    //        "https://runelist.io/api/vote/check?key=KEY&user={user}&ip={ip}"),
    // =====================================================================
    public static final Toplist[] SITES = {
        // Add your toplists here.
    };

    /** Vote tickets granted per confirmed vote (per site). */
    public static final int TICKETS_PER_VOTE = 1;

    /** Per-site cooldown between rewarded votes (matches the toplist's 12h window). */
    private static final long CLAIM_COOLDOWN_MS = 12L * 60L * 60L * 1000L;

    private VoteHandler() {
    }

    /**
     * Entry point for the {@code ::vote} command.
     */
    public static void vote(Player player) {
        if (SITES.length == 0) {
            player.getPacketSender().sendMessage("Voting isn't set up yet - check back soon!");
            return;
        }

        player.getPacketSender().sendMessage("@blu@Vote for us to earn Vote tickets - spend them in the Vote Shop (::voteshop):");

        boolean anyCheck = false;
        for (Toplist site : SITES) {
            if (!isBlank(site.voteUrl)) {
                player.getPacketSender().sendMessage("@blu@" + site.name + ": @bla@" + site.voteUrl);
            }
            if (!isBlank(site.checkUrl)) {
                anyCheck = true;
            }
        }

        if (!anyCheck) {
            player.getPacketSender().sendMessage("Your tickets will be credited automatically once voting is fully enabled.");
            return;
        }

        final String username = player.getUsername();
        final String ip = player.getHostAddress() == null ? "" : player.getHostAddress();
        final long now = System.currentTimeMillis();

        boolean checkedAny = false;
        for (Toplist site : SITES) {
            if (isBlank(site.checkUrl)) {
                continue;
            }

            // Skip sites already rewarded within the cooldown window.
            Long last = player.getVoteClaims().get(site.name);
            if (last != null && now - last < CLAIM_COOLDOWN_MS) {
                long hrsLeft = (CLAIM_COOLDOWN_MS - (now - last)) / (60L * 60L * 1000L);
                player.getPacketSender().sendMessage(site.name + ": already claimed - try again in ~"
                        + Math.max(1, hrsLeft) + "h.");
                continue;
            }

            checkedAny = true;
            startCheck(player, site, username, ip);
        }

        if (checkedAny) {
            player.getPacketSender().sendMessage("Checking your vote status...");
        }
    }

    /** Verifies a single site's vote off the game thread. */
    private static void startCheck(Player player, Toplist site, String username, String ip) {
        Thread worker = new Thread(() -> {
            boolean claimable = false;
            try {
                String url = site.checkUrl
                        .replace("{user}", URLEncoder.encode(username, "UTF-8"))
                        .replace("{ip}", URLEncoder.encode(ip, "UTF-8"));
                claimable = isVoteClaimable(httpGet(url));
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (claimable) {
                // Marshal the actual grant + cooldown bookkeeping onto the game thread.
                player.addPendingVoteSite(site.name);
            } else {
                // Netty channel writes are thread-safe, so messaging here is fine.
                player.getPacketSender().sendMessage(site.name + ": no new vote found yet. "
                        + "Vote using the link, then type ::vote again.");
            }
        }, "VoteCheck-" + site.name + "-" + username);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Grants tickets for any sites confirmed off-thread. Called from
     * {@code Player#process} on the game thread, so container mutation and the
     * cooldown map are safe to touch here.
     */
    public static void grantPendingRewards(Player player) {
        long now = System.currentTimeMillis();
        int totalTickets = 0;
        int claims = 0;

        String site;
        while ((site = player.getPendingVoteSites().poll()) != null) {
            // Re-check the cooldown on the game thread (guards against the same
            // site being enqueued twice by overlapping ::vote calls).
            Long last = player.getVoteClaims().get(site);
            if (last != null && now - last < CLAIM_COOLDOWN_MS) {
                continue;
            }
            player.getVoteClaims().put(site, now);
            totalTickets += TICKETS_PER_VOTE;
            claims++;
        }

        if (totalTickets <= 0) {
            return;
        }

        player.setTotalVotes(player.getTotalVotes() + claims);

        // Tickets are stackable, so they always fit if the player already holds
        // some or has a free slot; otherwise fall back to the bank.
        if (player.getInventory().getFreeSlots() > 0
                || player.getInventory().contains(ItemIdentifiers.VOTE_TICKET)) {
            player.getInventory().add(new Item(ItemIdentifiers.VOTE_TICKET, totalTickets));
        } else {
            player.getBank(0).add(new Item(ItemIdentifiers.VOTE_TICKET, totalTickets));
            player.getPacketSender().sendMessage("Your inventory was full, so your Vote tickets went to your bank.");
        }

        player.getPacketSender().sendMessage("@gre@Thanks for voting! You received " + totalTickets
                + " Vote ticket(s). Spend them in the Vote Shop (::voteshop).");
    }

    /**
     * Interprets the toplist's vote-check response. Default convention: a body
     * of "1" means there's an unclaimed vote. Edit to match your toplist(s).
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
