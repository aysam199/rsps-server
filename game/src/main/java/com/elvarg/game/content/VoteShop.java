package com.elvarg.game.content;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.elvarg.game.content.abilities.Ability;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.container.shop.Shop;
import com.elvarg.game.model.container.shop.ShopManager;
import com.elvarg.game.model.container.shop.currency.ShopCurrencies;
import com.elvarg.util.ItemIdentifiers;
import com.elvarg.util.Misc;
import com.elvarg.util.ShopIdentifiers;

/**
 * The Vote Shop - where players spend the {@code Vote ticket}s they earn from
 * voting (see {@link VoteHandler}).
 *
 * <p>It uses the standard shop interface with Vote tickets as the currency and
 * stocks two kinds of reward:</p>
 * <ul>
 *   <li><b>Ability items</b> - every custom ability, priced deliberately high
 *       in tickets so voting is a slow alternative route to abilities rather
 *       than a shortcut (they remain cheaper to buy with gold).</li>
 *   <li><b>Cosmetics</b> - vanity items that gold can't buy, the main draw of
 *       voting.</li>
 * </ul>
 *
 * <p>Coins and ability upgrades are also purchasable with tickets, but those
 * are handled through the Ability Master's dialogue / upgrade menu rather than
 * this interface (they need bundle amounts and per-ability targeting).</p>
 *
 * @author Custom
 */
public final class VoteShop {

    /** Flat ticket price for any ability item. Kept high so abilities stay a grind. */
    public static final int ABILITY_TICKET_PRICE = 12;

    /** Coins handed out per ticket when exchanging tickets for gold. */
    public static final int COINS_PER_TICKET = 50_000;

    /**
     * Cosmetic stock: item id -> ticket price. Purely vanity items, so they're
     * the most attractive use of tickets without affecting balance.
     */
    private static final Map<Integer, Integer> COSMETICS = new LinkedHashMap<>() {{
        put(ItemIdentifiers.BUNNY_EARS, 5);
        put(ItemIdentifiers.GREEN_HALLOWEEN_MASK, 12);
        put(ItemIdentifiers.BLUE_HALLOWEEN_MASK, 12);
        put(ItemIdentifiers.RED_HALLOWEEN_MASK, 12);
        put(ItemIdentifiers.SANTA_HAT, 18);
        put(ItemIdentifiers.RED_PARTYHAT, 30);
        put(ItemIdentifiers.YELLOW_PARTYHAT, 30);
        put(ItemIdentifiers.BLUE_PARTYHAT, 30);
        put(ItemIdentifiers.GREEN_PARTYHAT, 30);
        put(ItemIdentifiers.PURPLE_PARTYHAT, 30);
        put(ItemIdentifiers.WHITE_PARTYHAT, 30);
    }};

    /** Combined item id -> ticket price lookup used by the shop. */
    private static final Map<Integer, Integer> PRICES = new LinkedHashMap<>();

    private static final Shop SHOP;

    static {
        for (Ability ability : Ability.values()) {
            PRICES.put(ability.getItemId(), ABILITY_TICKET_PRICE);
        }
        PRICES.putAll(COSMETICS);

        Item[] stock = PRICES.keySet().stream()
                .map(id -> new Item(id, Shop.INFINITY))
                .toArray(Item[]::new);

        SHOP = new Shop(ShopIdentifiers.VOTE_SHOP, "Vote Shop", stock, ShopCurrencies.VOTE_TICKET.get());
        ShopManager.shops.put(SHOP.getId(), SHOP);
    }

    private VoteShop() {
    }

    /** Touched at startup so the shop is registered before anyone opens it. */
    public static void init() {
        // The static block above does the work; this just forces class loading.
    }

    /** The ticket price for a vote-shop item, or 0 if the shop doesn't sell it. */
    public static int ticketPrice(int itemId) {
        return PRICES.getOrDefault(itemId, 0);
    }

    /**
     * Exchanges up to {@code tickets} Vote tickets for coins
     * ({@link #COINS_PER_TICKET} each). Exchanges only as many as the player
     * actually has.
     */
    public static void exchangeForCoins(Player player, int tickets) {
        int held = player.getInventory().getAmount(ItemIdentifiers.VOTE_TICKET);
        int spend = Math.min(tickets, held);
        if (spend <= 0) {
            player.getPacketSender().sendMessage("You don't have any Vote tickets to exchange.");
            return;
        }
        player.getInventory().delete(new Item(ItemIdentifiers.VOTE_TICKET, spend));
        long coins = (long) spend * COINS_PER_TICKET;
        grantCoins(player, coins);
        player.getPacketSender().sendMessage("@gre@Exchanged " + spend + " Vote ticket(s) for "
                + Misc.insertCommasToNumber(Long.toString(coins)) + " coins.");
    }

    /** Adds coins to the inventory, falling back to the bank when it's full. */
    private static void grantCoins(Player player, long amount) {
        int safe = (int) Math.min(Integer.MAX_VALUE, amount);
        if (player.getInventory().getFreeSlots() > 0 || player.getInventory().contains(ItemIdentifiers.COINS)) {
            player.getInventory().add(new Item(ItemIdentifiers.COINS, safe));
        } else {
            player.getBank(0).add(new Item(ItemIdentifiers.COINS, safe));
            player.getPacketSender().sendMessage("Your inventory was full, so the coins went to your bank.");
        }
    }

    /** Opens the Vote Shop interface for the player. */
    public static void open(Player player) {
        player.getPacketSender().sendMessage("@blu@Welcome to the Vote Shop! Spend the Vote tickets you earn from voting.");
        player.getPacketSender().sendMessage("You have @red@"
                + Misc.insertCommasToNumber(Integer.toString(player.getInventory().getAmount(ItemIdentifiers.VOTE_TICKET)))
                + "@bla@ Vote ticket(s).");
        player.getPacketSender().sendMessage("@dre@Tip:@bla@ right-click an ability and choose Examine to see what it does.");
        ShopManager.open(player, ShopIdentifiers.VOTE_SHOP);
    }
}
