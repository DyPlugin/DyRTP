# DyRTP 1.0.1

This maintenance release fixes ordinary-player RTP failures, keeps teleportation free without an economy provider and adds shorter commands while preserving DyRTP's command-based experience.

## Fixed and improved

- Keep the selected destination chunk loaded throughout warmup. Shared tickets are released after completion, failure, cancellation, reload or shutdown; final terrain and protection checks remain in place.
- Keep RTP free when Vault or a registered economy provider is absent, including when an older configuration still contains a positive price. Reject positive prices and enabling payments until a provider is detected; provider transaction failures still stop paid requests safely.
- Detect economy provider registration changes without requiring payments to be enabled first.
- Add `/dyr` as a shortcut for `/dyrtp`, and `/dyra <operation>` for `/dyrtp admin <operation>`, with the same permissions and localized TAB suggestions.
- Use Turkish `Oto`/`oto` in language labels, help and TAB. Keep `otomatik` accepted and update unchanged stock labels without replacing custom translations.
- Remove the help command's own descriptive row from the help menu.
- Use `1.0.1` for the new tag and `DyRTP-1.0.1.jar` for the download, without a `v` prefix. Historical release tags and files remain unchanged.

## Verification

90 automated tests passed. Targeted live checks used an actual non-OP Java protocol client on Paper 26.1.2 / Java 25: normal RTP with warmup, `/dyr`, `/dyra` permission denial and free RTP with stored paid settings but no Vault/provider. The complete Minecraft/platform range and live economy transactions were not individually retested.

The controlled BetterRTP comparison was repeated for this artifact. Command-to-teleport measurements describe the documented setup, not CPU/RAM usage or a universal speed/safety guarantee.

![DyRTP and BetterRTP measured teleport time](https://raw.githubusercontent.com/DyPlugin/DyRTP/1.0.1/docs/images/DyRTP-vs-BetterRTP-en.png)

[Comparison method and results](https://github.com/DyPlugin/DyRTP/blob/1.0.1/docs/betterrtp-comparison.md) · [Verification details](https://github.com/DyPlugin/DyRTP/blob/1.0.1/docs/verification.md)

## Updating

Stop the server, back up the existing JAR and complete `plugins/DyRTP` folder, replace the JAR with **DyRTP-1.0.1.jar**, then restart. Keep your existing configuration, translations and player data. Missing defaults are added without resetting custom values. Core RTP needs no other plugin; paid RTP requires Vault and a registered economy provider.
