# Incog-Shop economy design

Incog-Shop combines four trading systems while using one economy balance through Vault:

- Global market: material-based, player-supplied stock with dynamic prices.
- Bulk sell GUI: safely converts eligible vanilla items into global market stock.
- Physical player shops: exact ItemStack sales backed by the actual chest/barrel inventory.
- Auction House: exact ItemStack listings for custom or otherwise unusual items, with Auction and Buy It Now modes.

Global market stock defaults to 750 for materials that do not yet have saved market data. Existing saved stock is never overwritten during an upgrade.

Netherite (scrap, ingot, block, every tool, every armor piece) and every shulker box color are hard-restricted to Sell Only: players can sell them into the market, but nothing can buy them back from server stock or from a Buy Order. This is enforced at the code level, not just a configurable default.

Item prices, market modes, and category/subcategory overrides are admin-editable in `shop-items.yml`. Live stock and demand pressure are kept separately in `market.yml`, since those change during normal play.

Auction bids use escrow. The current high bid is withdrawn immediately, and the previous high bidder is refunded when outbid. Expired auctions pay the seller after tax and place the item in the winner's claim queue. Payouts to an offline seller/owner/bidder never cancel a sale - they are delivered immediately if possible, otherwise queued and paid out automatically the next time that player joins.
