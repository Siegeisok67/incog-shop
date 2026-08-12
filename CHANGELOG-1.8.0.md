# Incog-Shop 1.8.0

### Economy fixes
- Fixed the root cause of "Seller payment failed; purchase was cancelled safely." on Auction
  House Buy It Now purchases: paying an offline seller could be rejected by the Vault-linked
  economy plugin, which cancelled the whole sale. Deposits to a third party (an auction seller,
  a bid refund, a filled market order's counterparty, a player-shop owner) now go through a new
  guaranteed-delivery path that creates the recipient's economy account if it's missing, and
  queues the payout to be delivered automatically the next time they join if it's still rejected.
  Nobody is shortchanged, and no sale is cancelled for this reason anymore.
- Fixed a related bug in `ShopManager`: a player-shop owner's payout was previously not even
  checked for success, so a failed deposit silently destroyed the money. It now uses the same
  guaranteed-delivery path.
- Fixed the same class of bug in `MarketOrderManager`: a filled Buy/Sell Order's payout to the
  resting order's owner (often offline) could abort the whole match and effectively stall further
  fills for that order; order cancellation refunds had the same issue when an admin cancelled
  someone else's order.

### Netherite & shulker boxes
- Netherite in every form (scrap, ingot, block, every tool, every armor piece) is now
  hard-restricted to Sell Only: it can be sold to the market, but nothing can buy it back -
  not the instant market buy, not a Buy Order, not the admin item-add command. This is enforced
  in `MarketManager` itself, not just a configurable default, so it can't be reverted by editing
  `shop-items.yml`.
- Every shulker box color is restricted the same way.
- Closed a loophole where a Buy Order could still acquire a Sell Only material even though the
  instant "buy" command already blocked it.
- Shulker boxes now have a sensible default price instead of falling through to the generic
  8.0 default.

### Sell Orders
- Players can already set their own price for a Sell Order; that price must now stay within a
  configurable range of the item's base price (`market-orders.sell-price-min-multiplier` /
  `sell-price-max-multiplier`, default 0.5x-2.0x). The Order Book GUI shows this range on the
  item's info icon.

### Sell Wand
- New `/sellwand` command hands out a wand item. Right-clicking a chest, trapped chest, barrel,
  or any shulker box with it instantly sells every eligible plain item inside to the server
  market, without opening the container - the same rules as the `/sell` GUI apply (custom,
  enchanted, or renamed items are left behind). Configurable via `sell-wand` in `config.yml`.

### Search & chat-input fixes
- Market search, custom Auction House bid entry, and Buy/Sell Order amount+price entry were all
  listening for the legacy `AsyncPlayerChatEvent`, which no longer reliably fires on current
  Paper. All three now use Paper's `AsyncChatEvent`. Search works again.

### Stock & pricing
- Base/starting market stock changed from 1,000 to 750, shared server-wide as before: buying
  lowers it for everyone, selling raises it back, and price reacts to scarcity. Existing saved
  stock is never lowered by this change.

### Admin configuration
- Item prices, market modes (Buy & Sell / Sell Only / Disabled), and category/subcategory
  overrides now live in a new `shop-items.yml`, separate from `config.yml` and from `market.yml`
  (which now holds only live stock and demand pressure). Existing servers are migrated
  automatically on first load after upgrading; nothing is lost.

### Category fixes
- Prismarine blocks, bricks, stairs, slabs, and walls, and Bone Blocks, were incorrectly grouped
  under "Mob Drops" - only the actual drops (Prismarine Shard, Prismarine Crystals) belong there.
  They now fall through to the general Blocks category, like other building materials that don't
  have a more specific home.
- Honey Blocks and Honeycomb Blocks were incorrectly grouped under "Farming & Food" alongside
  Honeycomb and Honey Bottle (which are food-adjacent and stay there). They now fall through to
  Blocks as well.

### Logging
- Added a standardized, human-readable transaction log written to console and to a new
  `transactions.log`, alongside the existing tab-separated `audit.log`:
  `[Incog-Shop] =|= 'user' (uuid) | Sold/Bought/Created Sell Offer/Created Buy Offer/Bid on/
  Created Auction for ... <|> timestamp`

### Build system
- Switched from Gradle to Maven. `build.gradle.kts` and `settings.gradle.kts` are removed;
  `pom.xml` replaces them. The `paper-api` dependency uses Maven's version-range syntax
  `[26.2.build,)` (Maven has no `+` wildcard) to resolve the latest 26.2 build automatically;
  pin it to a specific build like `26.2.build.112-stable` instead if you want a reproducible build.
