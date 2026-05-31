package com.elvarg.game.content;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * A tiny HTTP listener that receives "vote callbacks" from RSPS toplists
 * (RuneLocus, RuneList, RSPS.org, ...). When a player votes, the toplist sends a
 * request to a callback URL you configure on its dashboard; this server accepts
 * that request, validates the per-site secret, and records the vote via
 * {@link VoteHandler#handleCallback}. The ticket is then credited on the game
 * thread the next time the player ticks (see {@code Player#process}).
 *
 * <p>Configure the callback URL on each toplist as:
 * {@code http://<your-ip>:<CALLBACK_PORT>/vote/<SiteName>?key=<secret>}. The
 * toplist appends the player's username; we read it from any of the common
 * query params (user/username/name/playername/userid/callback) or from the
 * trailing path segment (e.g. {@code /vote/RuneTopic/<username>}).</p>
 *
 * <p>Uses only the JDK's built-in {@code com.sun.net.httpserver} so there are no
 * extra dependencies. Failure to start is logged but never crashes the game
 * server.</p>
 *
 * @author Custom
 */
public final class VoteCallbackServer {

    private static final Logger logger = Logger.getLogger(VoteCallbackServer.class.getSimpleName());

    private static HttpServer server;

    private VoteCallbackServer() {
    }

    /** Starts the listener on {@link VoteHandler#CALLBACK_PORT}. Safe to call once at boot. */
    public static void start() {
        if (server != null) {
            return;
        }
        // Nothing to receive callbacks for yet; skip binding the port.
        if (VoteHandler.SITES.length == 0) {
            logger.info("Vote callback server not started (no toplists configured in VoteHandler.SITES).");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(VoteHandler.CALLBACK_PORT), 0);
            server.createContext("/vote", VoteCallbackServer::handle);
            server.createContext("/", VoteCallbackServer::health);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "VoteCallback");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            logger.info("Vote callback server listening on port " + VoteHandler.CALLBACK_PORT
                    + " (path /vote/<site>).");
        } catch (IOException e) {
            logger.warning("Failed to start vote callback server on port " + VoteHandler.CALLBACK_PORT
                    + ": " + e.getMessage());
            server = null;
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void health(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "LeagueScape vote callback online.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();          // /vote/RuneLocus or /vote/RuneLocus/Name
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

            // Path segments after "/vote/".
            String rest = path.length() > "/vote".length() ? path.substring("/vote".length()) : "";
            while (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            String siteName = "";
            String pathUser = "";
            if (!rest.isEmpty()) {
                String[] seg = rest.split("/");
                siteName = decode(seg[0]);
                if (seg.length >= 2) {
                    pathUser = decode(seg[1]);
                }
            }

            String key = firstNonBlank(query.get("key"), query.get("secret"), query.get("apikey"));
            String user = firstNonBlank(query.get("user"), query.get("username"), query.get("name"),
                    query.get("playername"), query.get("userid"), query.get("callback"), pathUser);

            boolean ok = VoteHandler.handleCallback(siteName, key, user);
            logger.info("Vote callback: site=" + siteName + " user=" + user + " accepted=" + ok);

            // Most toplists are happy with any 200; "1" is the common convention.
            respond(exchange, 200, ok ? "1" : "0");
        } catch (Exception e) {
            respond(exchange, 200, "0");
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = decode(pair.substring(0, eq)).toLowerCase();
            String v = decode(pair.substring(eq + 1));
            map.put(k, v);
        }
        return map;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
