package com.elvarg.game.model.container.shop.currency.impl;

import com.elvarg.util.ItemIdentifiers;

/**
 * Shop currency for Vote tickets - the reward earned by voting for the server.
 * Used by the Vote Shop.
 *
 * @author Custom
 */
public class VoteTicketCurrency extends ItemCurrency {

    public VoteTicketCurrency() {
        super(ItemIdentifiers.VOTE_TICKET);
    }
}
