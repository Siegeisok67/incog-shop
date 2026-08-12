# Incog-Shop 1.8.4

- Fixed XP Vault persistence across server restarts.
- `xp-vault.yml` is now loaded during plugin startup.
- XP Vault data is also reloaded by Incog-Shop's reload workflow.
- XP Vault balances remain keyed by player UUID, so a player does not need to be online for their stored balance to exist.
- Deposits and withdrawals still save immediately, and normal autosaves/onDisable provide additional persistence.
