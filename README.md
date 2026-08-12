# Incog-Shop 1.8.0

Developer: **SnipeyFresh**

Incog-Shop is a Purpur/Paper 26.2 economy plugin with a dynamic server market, player physical shops, Vault economy support, an Auction House, bulk selling, and player Buy/Sell Orders.

## Economy
By default `economy.mode: VAULT`. Incog-Shop uses the economy provider registered through Vault, so with Vault + ExcellentEconomy all balances are the players' existing ExcellentEconomy balances. No balance copy is required.

Payouts to a third party in someone else's transaction (an Auction House seller, a refunded bidder, a filled market order's counterparty, a player-shop owner) are never lost and never cancel the sale: if the economy provider rejects the deposit (most often because that player is offline and has no account yet), Incog-Shop creates the account and retries, and if it's still rejected, the payout is queued and delivered automatically the next time that player joins.

## Global market
- Category -> subcategory -> item layout with centered buttons and search.
- 750 initial stock bootstrap for every globally tradable material, shared server-wide; buying lowers it for everyone, selling raises it back, and instant-buy/sell price reacts to scarcity.
- Netherite in every form (scrap, ingot, block, every tool, every armor piece) and every shulker box color can be sold to the market but never bought back, from server stock or from a Buy Order. Ancient Debris remains excluded entirely.
- Instant buys remove server stock. Instant sells remove the player's actual items, add server stock, then pay through Vault.
- `/sell` opens a safe bulk-sell GUI. `/sellwand` gives a wand that does the same thing instantly for a chest, barrel, or shulker box's contents - no GUI needed.
- Item prices, market modes, and category/subcategory overrides are admin-editable in `shop-items.yml` (separate from `config.yml` and from `market.yml`, which now holds only live stock and demand pressure).

## Market Buy/Sell Orders
Middle-click any item in `/market` to open its Order Book.
- Buy Orders escrow the full maximum purchase amount through Vault.
- Sell Orders escrow the actual plain items from the player's inventory, and the price must fall within a configurable range of the item's base price (`market-orders.sell-price-min/max-multiplier`, default 0.5x-2.0x).
- Buy Orders cannot be placed on a Sell Only material (netherite, shulker boxes) - the same restriction as the instant market buy.
- Highest Buy Order and lowest Sell Order receive price priority; older orders win ties.
- Compatible orders match automatically. The resting (older) order sets the execution price.
- If a Buy Order executes below its maximum price, the difference is refunded.
- Filled items are delivered immediately when possible or stored in a persistent claim queue.
- Manage orders from the GUI or with `/market orders`, `/market myorders`, `/market cancelorder <id>`, and `/market claim`.
- Direct commands: `/market buyorder <material> <amount> <price-each>` and `/market sellorder <material> <amount> <price-each>`.

## Auction House
`/ah` opens the Auction House. Players can list the held item/stack as either a timed Auction or Buy It Now listing. Bids are escrowed and previous bidders are refunded. Won/returned items can be claimed safely.

## Price shorthand
Player shops, Auction House prices/bids, admin prices, and market orders accept shorthand such as `10k`, `2.5m`, `5m`, `1.2b`, and `1t`.

## Physical player shops
The physical chest/barrel inventory is the shop stock. Owners sneak-right-click to open/restock it with exact matching items.

## Sell Wand
`/sellwand` gives a wand item (needs `incogshop.sellwand`, granted by default). Right-click a chest, trapped chest, barrel, or any shulker box color with it to instantly sell every eligible plain item inside to the server market - the container doesn't open, and the same eligibility rules as `/sell` apply (custom, enchanted, or renamed items are left behind). Configure allowed container types (shulker boxes are always allowed) with `sell-wand.containers` in `config.yml`, or disable the feature with `sell-wand.enabled: false`.

## Transaction log
Every market buy/sell, sell-order and buy-order creation, and auction bid/listing writes a standardized line to the console and to `transactions.log`:
```
[Incog-Shop] =|= 'PlayerName' (uuid) | Sold Diamond x64 for $576.00 at $9.00 per <|> 2026-08-08 14:23:01
```
This is separate from the existing tab-separated `audit.log`, which remains the machine-readable record for admin tooling.

## Build
Build with Maven (Java 25 toolchain):
```
mvn clean package
```
Output: `target/Incog-Shop-1.8.0.jar`.

Before building, `pom.xml` pins the `paper-api` dependency to `[26.2.build,)`, Maven's version-range equivalent of Gradle's `26.2.build.+` (Maven has no `+` wildcard). This resolves to the latest published 26.2 build automatically. For a reproducible pinned build instead, replace it with a specific version like `26.2.build.112-stable` - check the available builds at https://repo.papermc.io/service/rest/repository/browse/maven-public/io/papermc/paper/paper-api/.


## Economy provider selection (1.5.1)
By default Incog-Shop requires Vault to expose a provider whose name is exactly `ExcellentEconomy` (case-insensitive). Configure it with:
```yaml
economy:
  mode: VAULT
  ```
This prevents EssentialsX Economy from being selected when multiple Vault economy providers are installed.


## Discord price checks (1.5.7)

Requires DiscordSRV 1.30.5+ on the server. Incog-Shop uses DiscordSRV's already-connected bot; no separate bot token is needed.

Set `discord-price-check.enabled: true` and paste the desired Discord text channel ID into `discord-price-check.channel-id`.

Examples:
- `!price diamond`
- `!price netherite ingot`
- `!price diamond 7h`
- `!price diamond 24h`
- `!pricehelp`

Historical data starts accumulating after 1.5.7 is installed; Incog-Shop cannot reconstruct prices from before the history feature existed.


## 1.7.0 admin customization

`/marketadmin layout` opens the GUI layout editor.

Custom category commands:
- `/marketadmin createcategory <id> <icon-material> <display name...>`
- `/marketadmin setcategory <material> <category-id|auto>`
- `/marketadmin deletecategory <id>`

Market item controls:
- `/marketadmin additem <material> <base-price> [buy_sell|sell_only|disabled]`
- `/marketadmin mode <material> <buy_sell|sell_only|disabled>`
- In the admin item browser, press **F** to cycle Buy & Sell → Sell Only → Disabled.

New permissions:
- `incogshop.admin.layout`
- `incogshop.admin.category`
- `incogshop.admin.item`
