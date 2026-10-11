# DyRTP 1.0.0 versus BetterRTP 3.6.13

Measured on 2026-10-11. The first comparison exposed speculative candidate chunk generation in DyRTP. The corrected search waits for one candidate chunk result per request, retaining batched cheap checks and configured destination preloading. A regression test failed before the correction; the final suite has 80 passing tests.

## Artifacts and environment

- Final DyRTP 1.0.0: 120,306 bytes, SHA-256 `F66C39A0309885306798AEBFFCDF4785576F422DE2C77DAA25843360D5929487`.
- Earlier DyRTP artifact: SHA-256 `4B7E5199CC8E042592B0F48F8A5B850AF5C2002C446DFC5B9676F79115903C7F`. Its measurements below are explicitly historical.
- BetterRTP 3.6.13: 496,799 bytes, SHA-256 `960C49CB7C9A03F31D2DD751F83A78479B3465BCB5EB65789B19874E13D503F2`. Embedded plugin.yml confirmed the version. The [official Spigot resource](https://www.spigotmc.org/resources/betterrtp-random-wild-teleport.36081/) download returned HTTP 403; the artifact came from the Spiget mirror for resource 36081. The older GitHub release was not substituted.
- Paper 26.3 build 143, Java 25, Windows, `-Xms256M -Xmx768M`, actual local TCP client, creative player, localhost port 25579.
- Plugins ran separately, each with the common measurement probe. The same saved flat-world snapshot, including all dimensions, was restored before each phase. Production servers and sibling sources were untouched. All phases stopped normally.

## Common methodology

The probe dispatches the namespaced normal player command and measures wall time until an uncancelled MONITOR-priority teleport event, then checks actual arrival on the next tick. Both plugins use the same command-to-event boundary, excluding network/rendering and later chunk work.

Delay, cooldown, economy, automatic RTP, optional effects and protection hooks were disabled. Both used square shape, center 0/0, preload radius 1, maximum attempts 32, the same 23 block blacklist entries, empty biome lists and minimum distance zero where applicable. Small radius was 16–96; larger radius 500–1000. Height settings were -64 to 320 with runtime clamping. BetterRTP's Nether type was NETHER; its queue was disabled. DyRTP retained batch 32 and async/loaded-chunk preferences.

The final DyRTP phase ran around 15:20–15:23 local time, followed by BetterRTP from the restored snapshot. Each followed small first pass, small second pass, larger first pass, Nether and End. The earlier probe retained a dormant integration-query branch; no integration plugin was installed in timing phases and the stopwatch boundary was unchanged. The final probe removes that branch.

Basic landing checks cover solid non-blacklisted ground, feet/head clearance and border, not exhaustive terrain/event/economy safety. ChunkLoadEvent counts include player view, preload and repeated loads; new chunks use isNewChunk(). They are not plugin-only CPU/heap measurements.

## Final artifact results

![Median command-to-teleport time across five matched-setting samples](images/DyRTP-vs-BetterRTP-en.png)

The chart uses one shared 0–200 ms linear scale. Green represents DyRTP; gray represents BetterRTP. An [editable SVG](images/DyRTP-vs-BetterRTP-en.svg) is included.

| Scenario | Requests each | DyRTP median / p95 ms | BetterRTP median / p95 ms |
| --- | ---: | ---: | ---: |
| Overworld 16–96 first pass | 20 | 1.882 / 9.398 | 100.584 / 104.282 |
| Overworld 16–96 second pass | 20 | 0.966 / 2.184 | 100.076 / 101.896 |
| Overworld 500–1000 first pass | 10 | 49.291 / 52.993 | 115.279 / 130.660 |
| Nether 16–96 | 10 | 1.166 / 13.504 | 199.030 / 301.204 |
| End 16–96 | 10 | 0.746 / 1.204 | 199.022 / 500.171 |

Each plugin completed 70 requests with zero timeouts, arrival failures or failures of the basic landing check. Maximum times in the first two DyRTP rows were 226.916 and 2.439 ms, respectively; the first pass includes startup outliers.

The larger-radius median is approximately 7.8 times lower than the earlier DyRTP sample (383.324 ms), and approximately 2.3 times lower than this BetterRTP sample. These are sample ratios, not general guarantees.

| Scenario | DyRTP chunk loads / new chunks | BetterRTP chunk loads / new chunks |
| --- | ---: | ---: |
| Small first pass | 255 / 0 | 306 / 18 |
| Small second pass | 213 / 18 | 238 / 2 |
| Larger first pass | 515 / 421 | 624 / 525 |
| Nether | 217 / 23 | 263 / 79 |
| End | 363 / 0 | 399 / 36 |

Earlier DyRTP's larger-radius run recorded 650 loads / 589 new chunks and median/p95 383.324 / 550.606 ms. The correction removes the demonstrated speculative-loading mechanism, but random destinations and client view loading also influence totals. Do not attribute every count difference to search alone. The final JAR is about 76% smaller than BetterRTP; file size does not prove lower heap or CPU use.

## Evidence and limits

Probe/build/settings source: [scripts/comparison](../scripts/comparison/README.md). Raw logs/settings/client messages remain in `C:\Users\alph\AppData\Local\Temp\DyRTP-BetterRTP-comparison-20261011`: `dyrtp-optimized-comparison.log`, `betterrtp-repeat-comparison.log`, `optimized-small-config.yml`, `optimized-large-config.yml`, `rtp-client-evidence.jsonl`. Earlier results remain separately in `dyrtp-comparison.log` and `betterrtp-comparison.log`; they are not final-artifact tests.

One client, a flat Overworld, small samples and unseeded random target streams were used. Disk caches, JIT, GC and previous requests can affect results. Ten-sample p95 equals the maximum, not a stable population tail estimate. The second pass does not guarantee every target was loaded. Stock full profiles, many simultaneous players, real Bedrock/Geyser transport, a real Vault provider, production CPU/heap, queue preparation cost and other Minecraft releases were not compared. No universal speed or safety winner is established.

The host's OSHI registry errors occurred in both phases outside the RTP plugins. DyRTP's own integration checks are documented separately in [verification](verification.md); unsupported integrations are not used to judge the competing plugin.
