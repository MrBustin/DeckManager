# Card Deck Station Plan

Goal: add a station that can save a card deck layout and load that saved preset onto a compatible deck, such as swapping all current cards in a belt deck with another saved belt-compatible card set.

## Stage Status

- [x] Stage 1: Add a Card Deck Station block and temporary GUI.
- [x] Stage 2: Identify the Vault Hunters card deck item APIs, NBT structure, and compatibility rules for each deck type.
- [x] Stage 3: Add persistent station storage for named deck presets.
- [x] Stage 4: Add UI controls for reading a deck, saving a preset, selecting a preset, and previewing card contents.
- [x] Stage 5: Implement server-side validation and swapping so presets only load onto compatible decks.
- [x] Stage 6: Add station inventory for one deck and stored physical cards.
- [x] Stage 7: Convert save/load to move physical cards through station inventory instead of copying card NBT.
- [x] Stage 8: Add missing-card preview, swap planning, and safer failure messages.
- [ ] Stage 9: Add polish: recipe, model/texture pass, localization, failure messages, and regression testing.

## Completed Work

### Stage 1

Implemented on 2026-06-11 in the `DeckManager` project.

- Registered `card_deck_station` as a new block and block item.
- Added `CardDeckStationBlock`, which opens a menu on right-click.
- Added `CardDeckStationMenu` as a temporary server/client container bridge.
- Added `CardDeckStationScreen` as a temporary GUI confirming the block interaction path.
- Added client screen registration.
- Added basic blockstate/model/item model resources using an existing DeckManager texture as placeholder art.
- Added English localization for the block and container.

### Stage 2

Implemented on 2026-06-11 in the `DeckManager` project.

Inspected the resolved Vault Hunters jar:

`C:\Users\Justin\.gradle\caches\forge_gradle\deobf_dependencies\curse\maven\vault-hunters-official-mod-458203\7867725_mapped_official_1.18.2\vault-hunters-official-mod-458203-7867725_mapped_official_1.18.2.jar`

Relevant Vault Hunters APIs:

- `iskallia.vault.item.CardDeckItem`
  - `getId(ItemStack)` / `setId(ItemStack, String)`
  - `getCardDeck(ItemStack)` / `setCardDeck(ItemStack, CardDeck)`
  - `hasCardDeck(ItemStack)`
  - `getModifiersRoll(ItemStack)` / `setModifiersRoll(ItemStack, String)`
  - `getVersion(ItemStack)` / `setVersion(ItemStack, int)`
- `iskallia.vault.core.card.CardDeck`
  - `getUuid()` / `setUuid(UUID)`
  - `getSlots()`
  - `getCards()`
  - `getCard(CardPos)` / `setCard(CardPos, Card)`
  - `getModifiers()`
  - `getSocketCount()` / `setSocketCount(int)`
  - `writeNbt()` / `readNbt(CompoundTag)`
- `iskallia.vault.item.CardItem`
  - `getCard(ItemStack)`
  - `setCard(ItemStack, Card)`
  - `create(Card)`
- `iskallia.vault.core.card.Card`
  - `getGroups()`
  - `hasGroup(String)`
  - `writeNbt()` / `readNbt(CompoundTag)`

Important deck item NBT keys:

- `id`: deck config id, such as `belt`.
- `data`: serialized `CardDeck`.
- `deckModifierPool`: modifier roll/pool id.
- `version`: deck data version.

Important `CardDeck` NBT keys inside `data`:

- `uuid`
- `cards`
- each `cards` entry contains `pos` and `card`
- `modifiers`
- `socketCount`

Vault deck config source:

- Runtime config: `run/config/the_vault/card/decks.json`
- Config class: `iskallia.vault.config.card.CardDeckConfig`

Deck layout rules from `decks.json`:

- `X` means no slot.
- `O` means a normal card slot.
- `A` means an arcane-only card slot.

Runtime slot validation from `CardDeckContainerMenu.DeckSlot.mayPlace`:

- Slot must be active.
- Stack item must be `CardItem`.
- If slot is arcane-only, the card must have group `Arcane`.
- If slot is normal, the card must not have group `Arcane`.

Compatibility rule for this mod:

- A saved preset should only load onto a target deck when every saved occupied `CardPos` exists in the target deck and each card passes the target slot's normal/arcane rule.
- The strict default should also require the same deck config id from `CardDeckItem.getId`, so a saved `belt` preset only loads onto another `belt` deck.
- A later optional relaxed mode could allow different deck ids if the target layout is a superset and all saved cards fit, but Stage 5 should start strict.

Known deck ids from the current runtime config:

- `starter`, `large`, `double`, `merchant`, `expanded`, `extended`, `gdungeon`, `gilded`, `ldungeon`, `living`, `anvil`, `odungeon`, `treasure`, `black`, `shadow`, `lost`, `relic`, `champion`, `runic`, `bishop`, `belt`, `mystery`, `idona`, `velara`, `wendarr`, `tenos`, `cake`, `pillager`, `arcane`, `skull`, `mutant`, `puzzle`, `cactus`, `ornate`, `villager`

Target example:

- `belt`: display name `The Belt Deck`; layout has 20 normal slots, 0 arcane slots, and 3 sockets.
- Belt presets should reject arcane cards because every belt slot is normal.

### Stage 3

Implemented on 2026-06-11 in the `DeckManager` project.

- Added `CardDeckStationBlockEntity` as the station's persistent storage owner.
- Added `ModBlockEntities` and registered the `card_deck_station` block entity type.
- Converted `CardDeckStationBlock` from a plain `Block` to a `BaseEntityBlock`.
- Changed right-click opening to use the station block entity as the menu provider.
- Registered block entities from `DeckManager`.

Persistent preset storage API:

- `getPresets()`
- `getPreset(String name)`
- `upsertPreset(DeckPreset preset)`
- `removePreset(String name)`
- `clearPresets()`

Stored preset payload:

- `Name`
- `SourceDeckId`
- `SourceDeckName`
- `DeckData`
- `CreatedAt`

NBT layout on the station block entity:

- Root key: `Presets`
- Each preset is a compound containing the stored preset payload above.

Implementation notes:

- `DeckData` is copied on save/load and when presets are inserted so callers cannot mutate stored data accidentally.
- `upsertPreset` replaces an existing preset by case-insensitive name.
- Storage changes call `setChanged()` and send a block update on the server.
- Stage 3 does not expose save/load actions in the GUI yet; Stage 4 should wire UI controls and packets into this storage API.

### Stage 4

Implemented on 2026-06-11 in the `DeckManager` project.

- Added a Forge `SimpleChannel` network layer for the station screen.
- Added client-to-server packets for requesting preset summaries and saving the held deck as a preset.
- Added a server-to-client packet for syncing lightweight preset summaries back into the open station screen.
- Added server-side held deck reading using `CardDeckItem.getCardDeck(stack)` and `CardDeck.writeNbt()`.
- Added basic validation for save requests:
  - player must still be near the station
  - target block entity must be a `CardDeckStationBlockEntity`
  - preset name must be non-empty
  - main hand or offhand must contain a Vault Hunters `CardDeckItem`
  - deck must have readable card deck data
- Replaced the placeholder station screen with:
  - preset name field
  - Save button
  - Refresh button
  - clickable saved preset list
  - Prev/Next selection buttons
  - selected preset preview showing name, deck id, source deck name, saved card count, and save time

Stage 4 stores the serialized card deck layout in the station and previews a summary only. It does not apply presets to target decks yet.

### Stage 5

Implemented on 2026-06-11 in the `DeckManager` project.

- Added a Load button to the station screen.
- Added a client-to-server packet for applying the named preset to the held deck.
- Added server-side validation before any deck mutation:
  - player must still be near the station
  - target block entity must be a `CardDeckStationBlockEntity`
  - preset must exist
  - main hand or offhand must contain a Vault Hunters `CardDeckItem`
  - held deck must have readable card deck data
  - held deck id must exactly match the preset source deck id
- Loading a preset replaces the target deck's cards with the preset cards.
- Loading preserves the target deck's UUID, socket count, and modifiers by copying the target deck's serialized data, replacing only its `cards` list, reading that merged data back into a `CardDeck`, then calling `CardDeckItem.setCardDeck`.

### Stage 6

Implemented on 2026-06-11 in the `DeckManager` project.

- Added a persistent Forge `ItemStackHandler` inventory to `CardDeckStationBlockEntity`.
- Inventory layout:
  - slot 0: one Vault Hunters `CardDeckItem`
  - slots 1-36: Vault Hunters `CardItem` storage
- Exposed the station inventory through the Forge item handler capability.
- Serialized the station inventory into the block entity NBT under `Inventory`.
- Added the station inventory slots and player inventory slots to `CardDeckStationMenu`.
- Added shift-click transfer rules:
  - decks move into the deck slot
  - cards move into card storage
  - station contents move back to player inventory
- Updated the station screen layout to show the deck slot, card storage grid, and player inventory.
- Dropped stored items when the station block is broken.

Stage 6 does not yet consume or move cards during preset save/load. Save/Load still use the held deck and copied card NBT from Stage 5 until Stage 7 converts the behavior to physical card movement.

### Stage 7

Implemented on 2026-06-11 in the `DeckManager` project.

- Converted Save and Load to operate on the deck in the station's deck slot instead of the player-held deck.
- Saving a preset now:
  - reads the deck in the station deck slot
  - saves its current card layout as the named preset
  - converts every card in the deck into a physical Vault Hunters `CardItem`
  - inserts those card items into station card storage
  - empties the deck through Vault Hunters' `CardDeckContainer` so both the item `inventory` tag and deck data are updated without deleting the slot layout
- Loading a preset now:
  - reads the deck in the station deck slot
  - requires the preset deck id to exactly match the station deck id
  - finds matching physical card items in station storage by comparing serialized card NBT
  - ignores Vault Hunters empty placeholder cards with no `entries`
  - removes those matching card items from storage
  - converts the target deck's current cards into physical `CardItem`s and returns them to station storage
  - applies the selected preset's serialized `cards` list to the target deck
- Added simulation checks before mutating anything:
  - saving fails if card storage cannot hold all cards from the deck
  - loading fails if any preset card is missing from station storage
  - loading fails if storage cannot hold the target deck's current cards after removing the preset cards
  - deck card replacement is validated before moving items

Stage 7 makes the station storage authoritative for physical card movement. The preset still stores serialized card data for matching and layout reconstruction, but applying a preset now requires matching physical cards in the station.

### Current Stop Point

Stopped on 2026-06-12 after fixing two Stage 7 bugs and discovering a current loading regression.

Recent fixes:

- Fixed the deck-slot deletion bug after saving by emptying decks through Vault Hunters' `CardDeckContainer` instead of replacing the serialized `cards` list directly.
- Added filtering so Vault Hunters empty placeholder cards with no `entries` are not converted into physical `CardItem`s.
- Updated preset preview card counts to ignore empty placeholder cards.
- Updated load matching to ignore placeholder card entries that have no `entries`.

Current loading regression fix:

- Fixed on 2026-06-12 by parsing serialized preset card tags through `Card.readNbt()` before deciding whether a card is real.
- `DeckPresetNetworking.getRequiredCardTags` now ignores cards whose parsed `Card` has no entries, including serialized placeholders with `entries: []`.
- `DeckPresetNetworking.cardMatches` now compares normalized `Card.writeNbt()` output for both the stored `CardItem` and the required preset card tag.
- `DeckPreset.cardCount()` now uses the same parsed-card entry check as load matching, so preview counts and required physical-card counts stay aligned.

### Stage 8

Implemented on 2026-06-12 in the `DeckManager` project.

- Added a server-side `LoadPlan` for each preset that calculates required cards, matched physical cards in station storage, missing cards, current deck cards that would be returned to storage, deck compatibility, and whether returned cards can fit.
- Reused the same load plan during actual Load requests so the preview and server validation stay aligned.
- Improved load failure messages with specific missing-card and storage-capacity counts.
- Extended preset summary sync packets with availability/loadability fields.
- Updated the station screen to show available preset cards, missing-card status, current-card return count, and load status.
- Load is disabled in the screen until the selected preset is compatible, all required physical cards are present, and returned deck cards can fit in station storage.
- The screen refreshes preset load plans periodically while open so moving cards in or out of station storage updates the preview without requiring a manual refresh.

Stage 8 UI bug fixes:

- The preset name field is blank by default and is no longer overwritten by periodic preset refreshes or preset selection.
- Saving with a blank name now chooses the next available default name such as `Preset 1`, `Preset 2`, and so on.
- Loading uses the selected preset directly instead of relying on the editable name field.
- Preset summaries now sync preview `CardItem` stacks, and selecting a preset renders that preset's cards over the right-side card grid.
- The visible right-side card grid is now a selected-preset preview instead of raw station storage.
- Physical station card-storage slots are hidden offscreen, preventing unrelated stored cards from rendering through the selected preset preview.
- Preset card previews are available even when no deck is inserted in the station deck slot.
- Preset previews now sync from the saved preset card data and track per-card physical availability; cards currently in station storage render normally, while saved cards that are no longer in storage render with the same filter-slot overlay style used by Sophisticated Core.
- Preset summaries now include the deck layout rows read from `run/config/the_vault/card/decks.json` and each preview card's saved `CardPos`.
- The selected preset preview now renders in the same shape as the source deck layout on Vault Hunters' atlas-rendered card deck background: `X` cells are not drawn, `O` cells render as normal slots using Vault Hunters' card slot texture, and `A` cells render as arcane-tinted slots.
- Preview cards are drawn in their saved deck coordinates instead of being compacted into a storage grid.
- Preview layouts use the same deck panel sizing and slot origin as Vault Hunters' `CardDeckScreen`: 20px deck-background padding with card item origins at background + 21px.
- Save and load now force an open container sync after station inventory mutations to reduce stale client-side slot contents.

## Notes For Next Session

- Stage 9 should add recipe/model/texture polish, localization for user-facing messages, and hands-on regression testing in a dev world.
- To empty a deck without damaging its layout, prefer `CardDeckContainer`: set each mapped slot to `ItemStack.EMPTY`, then call `setChanged()`. This matches Vault Hunters' own GUI save path and clears the item `inventory` tag as well as deck card data.
- Vault Hunters may represent empty deck slots as `Card` objects with no `entries`; do not convert those placeholders into physical `CardItem`s or require them for load matching.
- Do not mutate target decks by hand-writing raw NBT if avoidable; prefer Vault Hunters helpers such as `CardDeckContainer` or `CardDeckItem.setCardDeck` so refresh behavior runs.
- When saving from a deck item, preserve card data and positions. Do not preserve the target deck's original UUID unless there is a specific reason; Stage 3/5 should decide whether presets store UUID or generate/keep target UUID.
