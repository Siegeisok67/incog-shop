# Incog-Shop economy design

Incog-Shop combines four trading systems while using one economy balance through Vault:

- Global market: material-based, player-supplied stock with dynamic prices.
- Bulk sell GUI: safely converts eligible vanilla items into global market stock.
- Physical player shops: exact ItemStack sales backed by the actual chest/barrel inventory.
- Auction House: exact ItemStack listings for custom or otherwise unusual items, with Auction and Buy It Now modes.

Global market stock defaults to 1,000 for materials that do not yet have saved market data. Existing saved stock is never overwritten during an upgrade.

Auction bids use escrow. The current high bid is withdrawn immediately, and the previous high bidder is refunded when outbid. Expired auctions pay the seller after tax and place the item in the winner's claim queue.
