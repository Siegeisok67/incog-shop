# Incog-Shop 1.5.2

Developer: **SnipeyFresh** (and partially Siegeisok67)

Incog-Shop is a Purpur/Paper 26.2 economy plugin with a dynamic server market, player physical shops, Vault economy support, an Auction House, bulk selling, and player Buy/Sell Orders.

## Economy
By default `economy.mode: VAULT`. Incog-Shop uses the economy provider registered through Vault, so with Vault + ExcellentEconomy all balances are the players' existing ExcellentEconomy balances. No balance copy is required.

## Global market
- Category -> subcategory -> item layout with centered buttons and search.
- 1,000 initial stock bootstrap for every globally tradable material.
- Netherite items are tradable; Ancient Debris remains excluded.
- Instant buys remove server stock. Instant sells remove the player's actual items, add server stock, then pay through Vault.
- `/sell` opens a safe bulk-sell GUI.

## Market Buy/Sell Orders
Middle-click any item in `/market` to open its Order Book.
- Buy Orders escrow the full maximum purchase amount through Vault.
- Sell Orders escrow the actual plain items from the player's inventory.
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

## Build
Build with your Java 26 + Gradle setup:
```
gradle clean build
```
Output: `build/libs/Incog-Shop-1.5.0.jar`.


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


## 1.7.2 convenience & safety

- `/stash` opens persistent overflow storage.
- Market search uses a sign editor.
- `/sell` automatically processes when closed.
- Auction House Claims is now a GUI.
- The server market checks every minute for a due 72-hour restock. When due, BUY_SELL items under 100 stock are reset to a random 500-1000 stock.


## 1.7.6 GUI market setup

Open `/marketadmin gui` and click **Market Setup**.

From the GUI, admins can:
- Create custom categories using a sign-name prompt.
- Create custom subcategories inside those custom categories.
- Use the held item as the category/subcategory icon.
- Shift-click a custom category to assign the held market item to it.
- Click a custom subcategory to assign the held market item directly to it.
- Add the held vanilla item to the market without typing `/marketadmin additem`.
- Open the GUI Layout Editor and normal Admin Market from the same setup menu.

Custom categories with one or more custom subcategories automatically show a subcategory-selection GUI to players.


## Maven build

This source package now uses Maven. From the project root, run:

```bash
mvn clean package
```

The compiled plugin is written to `target/Incog-Shop-1.8.1.jar`. See `MAVEN-BUILD.md` for Arch Linux setup notes.


## 1.8.1 GUI redesign

`/marketadmin gui` now opens the **Admin Studio**. It provides centered GUI actions for category management, item organization, adding the held item, opening the admin market, toggling infinite stock, and editing layouts.

Category management controls:
- **Left-click** a custom category to manage its subcategories.
- **Shift-click** a custom category to assign the held market item.
- **Right-click** a custom category to open a safe removal confirmation.
- Create categories and subcategories with the centered green Create buttons.

The **Item Organizer** displays every market material and its current destination. Left-click an item to move it to a built-in/custom category or subcategory; right-click cycles its Buy/Sell mode.

The Layout Designer now has a **Reset This Layout** button. This is useful after upgrading from an older version if `gui-layout.yml` contains positions from the previous GUI defaults.
