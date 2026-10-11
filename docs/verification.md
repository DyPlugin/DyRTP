# Verification record: 1.0.0

Reviewed on 2026-10-11. The user explicitly selected stable 1.0.0 from 1.0.0-Beta. The review workspace had no Git metadata; original sources/build/docs/license were preserved at `C:\Users\alph\Documents\DyRTP-review-baseline-20261011`. No commit, push, publication or production installation occurred during the review. The user subsequently authorized a separate GitHub/Modrinth publication of 1.0.0 with the chart and release notes. DyClaim and other sibling project sources were not changed.

## Automated verification

The original project compiled but had no tests. Final `gradle clean test build` with Gradle 9.1.0 / Java 25 passed: **80 tests, zero failures/errors/skips**. Java compilation still targets release 17 (class major 61) and the original Purpur API 1.20.4 / Vault API 1.7.1. JUnit/Mockito and the separate integration probe are absent from the distribution JAR.

Regression coverage includes global language/permissions/help/TAB, exact dotted Bedrock player targets, missing-player rejection, invalid prices/config, safe staged reloads and player data preservation, DyClaim absence/negative coordinates/query failures/disabled state, safe height/headroom/Nether scan/End void, unloaded chunk policy and search limits, stale/cancelled callbacks, native cross-world respawn versus later world changes, redirected/failed teleports, confirmation tokens/settings/permission changes and single provider-bound refunds. Mocked provider and event tests are automated tests, not live economy coverage.

Failing regressions were observed before the relevant behavioral fixes where practical, including cancelled callback, disabled DyClaim, reload/configuration and cross-world respawn cases. The later dotted-name tests extend the already-fixed exact-name lookup. The final independent read-only review found no remaining important or critical defect in the reviewed changes; this is not a guarantee about every integration or event listener combination.

## Final artifact and real server checks

Final JAR: `C:\Users\alph\Documents\DyRTP\build\libs\DyRTP-v1.0.0.jar`. Embedded `plugin.yml`: `version: 1.0.0`, `api-version: '1.20'`.

SHA-256: `F66C39A0309885306798AEBFFCDF4785576F422DE2C77DAA25843360D5929487`.

Final build and the JAR copied into the isolated test servers have the same hash. Tests used Paper 26.3 build 143, Java 25 and an actual TCP protocol client on localhost port 25579, disposable worlds and bounded search radii. They did not use a production server or mocked Bukkit world. The table below records the earlier artifact `4B7E5199...03C7F`; it is historical integration evidence, not a claim that every scenario was repeated after the search optimization. Fresh final-artifact checks are recorded separately below. All phases ended with normal server `stop`.

| Earlier phase | Observed result |
| --- | --- |
| DyClaim absent | DyRTP enabled and reloaded from console; normal and forced RTP succeeded. |
| Dotted player `.BedrockReview` | Actual login, normal RTP and console force worked; `BedrockReview` without the dot was rejected as offline. English help/TAB followed global English despite Turkish client locale. |
| Native cross-world respawn | The dotted player died in Nether, respawned in Overworld and received automatic death RTP. |
| Damage during warmup | A three-second pending RTP was cancelled by real server damage; no subsequent success was sent. |
| DyClaim 1.0.2 installed | Real API probe passed eight checks: namespace owner, unclaimed query, no chunk load, negative unclaimed target, claim creation in negative chunk, owner claim rejected, actual search and newly claimed result rejected. |
| Language on final JAR | Global Turkish changed help and TAB to Turkish; non-admin TAB omitted admin commands. Normal RTP succeeded with Turkish messages. |
| DyClaim disabled | Ninth probe check rejected final validation; subsequent player RTP failed safely with one explanatory warning. |
| Native permissions | OP explicit denies removed use/admin/warmup bypass; denied TAB omitted RTP/admin; a non-OP with only the use leaf retained player access. |
| External potion effects | Existing blindness and blindness replaced by a stronger external effect survived warmup cancellation. |
| Malformed safety list | Scalar disabled-worlds reload was rejected; the invalid file hash stayed unchanged and working settings remained active. |
| Dimensions | Real forced RTP succeeded in Nether and End; resulting worlds were checked through entity data. |

Evidence is retained in `C:\Users\alph\AppData\Local\Temp\DyRTP-live-review-20261011`: `reverification-without-DyClaim.log`, `reverification-with-DyClaim.log`, `reverification-metrics.log`, `rtp-client-evidence.jsonl`. The final runs occurred around 14:44–14:52 local time. Earlier evidence files were from an earlier artifact and are not represented as final-JAR tests. Earlier malformed-YAML live rejection and automatic/client-language checks supplement the final regressions only.

The isolated probe source and usage are in [scripts/integration](../scripts/integration/README.md). The server emitted an OSHI Windows performance registry error and Paper/runtime warnings outside DyRTP; they are not hidden or presented as a clean server log.

## Contract and scope checks

### Final artifact after search optimization

Fresh `gradle clean test build` passed all 80 tests. The new pending-load regression failed against the earlier implementation, which launched 32 loads before the first result. The correction allows one candidate load per search, preserves cheap checks/preload settings and adds a single-retry/attempt-bound regression. An independent read-only review found no defect in this change.

The final artifact `F66C39A0...9487` completed 70 real normal-command RTP requests across Overworld, Nether and End with zero arrival or basic-landing-check failures. The fresh [BetterRTP comparison](betterrtp-comparison.md) uses the same stopwatch boundary and restored starting worlds, with 70 requests per plugin. It contains no integration-support comparison.

Around 15:26–15:29 local time, the final artifact was tested separately with real DyClaim 1.0.2: all eight integration checks passed, including negative chunk conversion, owner claims and claiming a real search target before final validation. The disabled-hook check passed; an actual subsequent request failed safely, with one explanatory warning. Three actual permission checks and four effect-preservation checks passed. The dotted account `.BedrockReview` successfully used normal and forced RTP; the undotted name was rejected and actual arrival was inspected. These remain offline Java protocol clients, not real Bedrock devices.

After normal shutdown, the server restarted with DyClaim absent and protection settings still enabled. Console reload, normal RTP and forced dotted-player RTP succeeded; arrival was inspected. Fresh logs are `optimized-with-DyClaim.log` and `optimized-without-DyClaim.log` in the existing isolated live-review folder. The final build and both isolated copied JARs have identical SHA-256. Historical tables above and finder-only timing samples below retain their earlier-artifact identity.

The repeat audit additionally reproduced and fixed scalar safety lists silently becoming empty and permission revocation during the public pre-teleport event being ignored. New tests cover malformed-list retention, revoked event permissions, stale warmup callbacks after cancellation/reload, quote expiry and ownership, refund opt-out, charged redirects, concurrency/timeout slot release and exact attempt bounds. Both behavioral fixes were observed failing before the fixes and passing afterward.

DyClaim source was compared with the actual `getClaimManager().isChunkClaimed(String worldName, int chunkX, int chunkZ)` API. Claim checks do not use build/trust access. DyRTP writes no DyClaim data file. Existing protection opt-outs and refund settings are preserved.

World/protection/player/economy checks and teleport run on the server thread; asynchronous chunk completion returns through the main scheduler. Async loading does not authorize arbitrary asynchronous Bukkit calls. This follows the [Bukkit scheduler contract](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/scheduler/BukkitScheduler.html). The existing update check performs HTTP asynchronously and schedules notifications on the server thread. Its real filtered Modrinth request returned HTTP 200 with an empty compatible feed; release selection, malformed responses and Beta-to-stable comparison have automated coverage.

## Limits and remaining integration risks

- No real Geyser/Floodgate server or Bedrock device was available. Dotted-name command handling was tested in mocks and on an actual offline Paper connection; Bedrock transport, account linking and device interaction remain unverified.
- No registered real Vault economy provider was available. Debit/refund/error scenarios passed mock tests only; uncertain provider outcomes require manual reconciliation and are not automatically retried.
- Normal Overworld and forced Nether/End RTP searches were live-tested in the small isolated worlds. Every Minecraft version, Folia and other protection adapters remain unverified; these checks do not cover every possible terrain configuration.
- Teleport redirects by already-registered listeners are revalidated, but plugins mutating events at MONITOR contrary to the event contract cannot be guaranteed safe by DyRTP.
- The updater's real empty response does not prove a real available-release notification. A separate competing-plugin timing comparison is linked below; no high-player-count load test or broad compatibility claim was made.

## Earlier artifact search timings

Each row contains 20 sequential real finder calls with fresh final terrain/claim validation, DyClaim enabled, radius 16–96, minimum distance 0, 240 maximum attempts, batch 32, preload radius 1 and `wait-for-preload: false`. Measured time is search-future completion, excluding deliberate player warmup/network/rendering and any later background preload completion. Worlds already had saved terrain; the first pass began with few chunks resident in memory. Resident chunk counts include the client and preload work and are not a count of generated chunks.

| World/pass | Median ms | p95 ms | Maximum ms | Mean attempts | Resident chunks before/after |
| --- | ---: | ---: | ---: | ---: | ---: |
| Overworld first | 1.164 | 60.366 | 188.327 | 5.55 | 1 / 80 |
| Overworld second | 0.315 | 1.336 | 3.246 | 1.05 | 121 / 142 |
| Nether | 1.318 | 14.624 | 100.934 | 11.90 | 1 / 17 |
| End | 0.590 | 1.530 | 5.407 | 10.30 | 1 / 36 |

These small-profile timings do not establish performance at the stock 500–5000 radius, on newly generated terrain or with many concurrent players. A later separate [BetterRTP comparison](betterrtp-comparison.md) measures both plugins at a common command-to-teleport boundary, including a larger-radius scenario. The product JAR bundles no test framework, probe or external dependency; process-wide server memory cannot be attributed to DyRTP from these runs.
