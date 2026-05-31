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
         * Optional legacy pull/verify API (server polls the toplist). Supports
         * {user} and {ip} placeholders. Leave blank for the callback model.
         */
        public final String checkUrl;
        /**
         * Shared secret for the callback model. The toplist calls our callback
         * endpoint (see {@link VoteCallbackServer}) when a player votes; we only
         * accept the callback if it carries this secret. You pick this value and
         * embed it in the callback URL you paste into the toplist dashboard, e.g.
         * {@code http://YOUR_IP:8085/vote/RuneLocus?key=THIS_SECRET}.
         * Leave blank to accept callbacks without a secret check (not recommended).
         */
        public final String secret;

        public Toplist(String name, String voteUrl, String checkUrl) {
            this(name, voteUrl, checkUrl, "");
        }

        public Toplist(String name, String voteUrl, String checkUrl, String secret) {
            this.name = name;
            this.voteUrl = voteUrl;
            this.checkUrl = checkUrl;
            this.secret = secret;
        }

        /** Convenience for the callback model (no pull/verify URL needed). */
        public static Toplist callback(String name, String voteUrl, String secret) {
            return new Toplist(name, voteUrl, "", secret);
        }
    }

    // =====================================================================
    //  Toplists are configured in a properties file that lives ONLY on the
    //  server (never committed), so the callback secrets stay private:
    //
    //      data/vote_sites.properties   (next to data/definitions/)
    //
    //  Format - one numbered block per site (site.1.*, site.2.*, ...):
    //      site.1.name=RuneLocus
    //      site.1.url=https://www.runelocus.com/vote/?id=YOURID
    //      site.1.secret=8bdbf4ba7dd2ae33cb2385beff62ac83
    //
    //  Then set each toplist's Callback URL (in its dashboard) to:
    //      http://82.70.213.105:8085/vote/<name>?key=<secret>
    //  The site appends the username; we read user/username/name/playername/
    //  userid/callback, or the trailing path segment. Edit the file and restart
    //  to add/change sites - no code redeploy needed.
    // =====================================================================
    public static final Toplist[] SITES = loadSites();

    /** Loads {@link #SITES} from {@code data/vote_sites.properties}, if present. */
    private static Toplist[] loadSites() {
        java.io.File file = new java.io.File("../data/vote_sites.properties");
        if (!file.exists()) {
            return new Toplist[0];
        }
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            props.load(in);
        } catch (Exception e) {
            e.printStackTrace();
            return new Toplist[0];
        }
        java.util.List<Toplist> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String name = props.getProperty("site." + i + ".name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String url = props.getProperty("site." + i + ".url", "").trim();
            String secret = props.getProperty("site." + i + ".secret", "").trim();
            list.add(Toplist.callback(name.trim(), url, secret));
        }
        return list.toArray(new Toplist[0]);
    }

    /** TCP port the callback HTTP listener binds to (open this in Oracle + firewall). */
    public static final int CALLBACK_PORT = 8085;

    /**
     * Votes confirmed by an incoming callback but not yet credited (e.g. the
     * player was offline, or hasn't ticked yet). Keyed by normalised username ->
     * (site name -> time recorded). Drained onto the game thread by
     * {@link #drainCallbackVotes(Player)}.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Long>> PENDING_CALLBACK =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Drop unclaimed callback votes after this long so the map can't grow forever. */
    private static final long PENDING_CALLBACK_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

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

        for (Toplist site : SITES) {
            if (!isBlank(site.voteUrl)) {
                player.getPacketSender().sendMessage("@blu@" + site.name + ": @bla@" + site.voteUrl);
            }
        }

        // Credit anything a toplist already confirmed via the callback endpoint.
        drainCallbackVotes(player);
        if (!player.getPendingVoteSites().isEmpty()) {
            grantPendingRewards(player);
        }

        // Legacy pull/verify sites (server polls the toplist) still work too.
        boolean anyCheck = false;
        for (Toplist site : SITES) {
            if (!isBlank(site.checkUrl)) {
                anyCheck = true;
                break;
            }
        }
        if (!anyCheck) {
            player.getPacketSender().sendMessage(
                    "After you vote, your ticket is credited automatically within a few seconds.");
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
     * Called by {@link VoteCallbackServer} when a toplist posts a confirmed vote
     * to our callback endpoint. Validates the site + secret and records the vote
     * for later crediting on the game thread.
     *
     * @param siteName the site segment from the callback URL (e.g. "RuneLocus")
     * @param key      the secret supplied by the caller (may be null)
     * @param username the voting player's in-game name (may be null)
     * @return true if the callback was accepted and recorded
     */
    public static boolean handleCallback(String siteName, String key, String username) {
        if (isBlank(siteName) || isBlank(username)) {
            return false;
        }

        Toplist match = null;
        for (Toplist site : SITES) {
            if (site.name.equalsIgnoreCase(siteName.trim())) {
                match = site;
                break;
            }
        }
        if (match == null) {
            return false;
        }

        // If a secret is configured, the callback must carry it exactly.
        if (!isBlank(match.secret) && !match.secret.equals(key)) {
            return false;
        }

        String norm = normalize(username);
        prunePendingCallbacks();
        PENDING_CALLBACK
                .computeIfAbsent(norm, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(match.name, System.currentTimeMillis());
        return true;
    }

    /** Cheap guard so {@code Player#process} only drains when something is pending. */
    public static boolean hasPendingCallbacks() {
        return !PENDING_CALLBACK.isEmpty();
    }

    /**
     * Moves any callback-confirmed votes for this player onto their per-account
     * pending queue. Safe to call from the game thread; the actual ticket grant
     * and 12h cooldown are still enforced by {@link #grantPendingRewards(Player)}.
     */
    public static void drainCallbackVotes(Player player) {
        if (PENDING_CALLBACK.isEmpty()) {
            return;
        }
        java.util.concurrent.ConcurrentHashMap<String, Long> sites =
                PENDING_CALLBACK.remove(normalize(player.getUsername()));
        if (sites == null) {
            return;
        }
        for (String site : sites.keySet()) {
            player.addPendingVoteSite(site);
        }
    }

    private static void prunePendingCallbacks() {
        long now = System.currentTimeMillis();
        PENDING_CALLBACK.forEach((user, sites) -> {
            sites.values().removeIf(ts -> now - ts > PENDING_CALLBACK_TTL_MS);
            if (sites.isEmpty()) {
                PENDING_CALLBACK.remove(user);
            }
        });
    }

    /** Normalises a username for matching (case- and separator-insensitive). */
    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase().replace('_', ' ');
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
