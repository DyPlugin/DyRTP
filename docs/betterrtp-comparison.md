# DyRTP 1.0.1 versus BetterRTP 3.6.13

Measured on 2026-10-23 with a real, non-OP local TCP client. These are fresh measurements of the 1.0.1 artifact, replacing the earlier 1.0.0 timing rows. They do not establish a universal performance or safety winner.

## Artifacts and environment

- DyRTP 1.0.1: 122,932 bytes, SHA-256 `F0D15352F6C622E97395674E0D37BED9421FDF6678BA689F9C0ACD6769A98FB6`.
- BetterRTP 3.6.13: 496,799 bytes, SHA-256 `960C49CB7C9A03F31D2DD751F83A78479B3465BCB5EB65789B19874E13D503F2`. Embedded plugin.yml confirmed the version. The artifact is the Spiget mirror download of [official Spigot resource 36081](https://www.spigotmc.org/resources/betterrtp-random-wild-teleport.36081/).
- [Paper 26.1.2 build 74](https://fill.papermc.io/v3/projects/paper/versions/26.1.2/builds/74), SHA-256 `1D70B1DAB9CF4A6DE615209A536F3A45A2186240253C428213CE2188AB95E5F7`; Java 25.0.4.1, Windows 11, `-Xms256M -Xmx768M`, localhost port 25579.
- A separate disposable server ran one RTP plugin at a time with the common comparison probe. The same saved flat-Overworld snapshot, including Nether, End and player data, was restored before the other plugin phase. The real client was `DyRtpReview`, creative and non-OP; `ops.json` was empty. The probe granted only the normal use/world permissions. All measured phases stopped normally.

Paper 26.3 build 214 bootstrapped separately, but was not used for these timings: the available [minecraft-protocol 1.68.0 client supports through 26.1](https://github.com/PrismarineJS/node-minecraft-protocol/blob/master/docs/README.md), while 26.3 uses protocol 777. Paper 26.1.2 uses the client's supported protocol 775. No custom protocol emulation or ViaVersion adapter was used. Earlier 26.3 results belonged to historical 1.0.0 work and are not results for this JAR.

## Common methodology

The probe dispatches the namespaced normal player command and measures wall time until an uncancelled MONITOR-priority teleport event, then confirms actual arrival on the following tick. Both plugins use the same command-to-event boundary, excluding network/rendering and later chunk work.

Delay, cooldown, economy, automatic RTP, optional effects and protection hooks were disabled. Both used square shape, center 0/0, preload radius 1, maximum attempts 32, the same 23 block blacklist entries, empty biome lists and minimum distance zero where applicable. Small radius was 16–96; larger radius 500–1000. Height settings were -64 to 320 with runtime clamping. BetterRTP's actual Nether world type was NETHER; its queue was disabled. DyRTP retained batch 32 and async/loaded-chunk preferences. Paper's chunk-unload delay was 10 seconds for both timing phases.

Each phase followed small first pass, small second pass, larger first pass, Nether and End. DyRTP timing ran at approximately 08:22–08:24 and BetterRTP at 08:27–08:29, Europe/Istanbul. Both started from the same saved snapshot, with unseeded random destination streams.

An initial BetterRTP attempt was excluded because the benchmark configuration writer left old same-indentation list entries after replacing YAML block lists. Its resulting invalid settings did not exercise the intended profile. The tooling was corrected, the saved baseline restored again and BetterRTP restarted before collecting every BetterRTP row below. The failed setup log is retained separately; it is not counted as a plugin failure.

Basic landing checks cover solid non-blacklisted ground, feet/head clearance and border. They are not exhaustive terrain, event, economy or claim tests. ChunkLoadEvent counts include player view, preload and repeated loads; new chunks use isNewChunk(). They are server-wide counts for the measured world, not plugin-only CPU or heap measurements.

## Final artifact results

![Median command-to-teleport time across five matched-setting samples](images/DyRTP-vs-BetterRTP-en.png)

The chart uses one shared 0–200 ms linear scale. Green represents DyRTP; gray represents BetterRTP. An [editable SVG](images/DyRTP-vs-BetterRTP-en.svg) is included.

| Scenario | Requests each | DyRTP median / p95 ms | BetterRTP median / p95 ms |
| --- | ---: | ---: | ---: |
| Overworld 16–96 first pass | 20 | 1.531 / 15.237 | 100.275 / 102.207 |
| Overworld 16–96 second pass | 20 | 1.023 / 2.248 | 100.138 / 100.849 |
| Overworld 500–1000 first pass | 10 | 52.658 / 161.901 | 116.091 / 233.119 |
| Nether 16–96 | 10 | 1.276 / 283.704 | 100.998 / 201.138 |
| End 16–96 | 10 | 0.733 / 1.385 | 100.658 / 217.543 |

Each plugin completed 70 requests with zero timeouts or arrival failures in the final timing samples. The basic landing predicate failed once in BetterRTP's Nether sample and never in DyRTP's samples. The probe recorded only the failure count, not the offending coordinates or predicate branch; this observation cannot identify its cause or establish a general safety comparison. Ten-sample p95 equals the sample maximum. DyRTP's Nether p95 is higher in this run even though its median is lower.

| Scenario | DyRTP arrival failures / basic check failures | BetterRTP arrival failures / basic check failures |
| --- | ---: | ---: |
| Overworld 16–96 first pass | 0 / 0 | 0 / 0 |
| Overworld 16–96 second pass | 0 / 0 | 0 / 0 |
| Overworld 500–1000 first pass | 0 / 0 | 0 / 0 |
| Nether 16–96 | 0 / 0 | 0 / 1 |
| End 16–96 | 0 / 0 | 0 / 0 |

| Scenario | DyRTP chunk loads / new chunks | BetterRTP chunk loads / new chunks |
| --- | ---: | ---: |
| Overworld 16–96 first pass | 193 / 188 | 194 / 192 |
| Overworld 16–96 second pass | 215 / 24 | 302 / 103 |
| Overworld 500–1000 first pass | 540 / 449 | 591 / 492 |
| Nether 16–96 | 227 / 200 | 256 / 249 |
| End 16–96 | 363 / 360 | 364 / 361 |

The complete aggregate rows, including maximum times and resident chunk counts, are saved in [results-20261023.json](../scripts/comparison/results-20261023.json). JAR size, chunk counts and command latency do not establish lower heap use, lower CPU consumption or a general throughput advantage.

## Separate 1.0.1 regression checks

These checks were outside the 70-request timing profiles and do not change the timing rows:

- With a real non-OP client, `/dyra` returned a permission error. `/dyr` used the ordinary three-second warmup and arrived successfully.
- With `economy.enabled: true`, cost 100 and no Vault installed, the same client completed `/dyr` without payment or a confirmation prompt. With Paper's chunk-unload delay set to zero, radius 500–1000 and warmup 3, the client received warmup at 05:24:54.002 UTC and the actual destination at 05:24:56.961 UTC. The server recorded that Vault was absent and RTP remained free.
- Console `/dyra fiyat 100` was rejected with the missing-provider message; it did not enable payment.
- The official historical [DyRTP 1.0.0 JAR](https://github.com/DyPlugin/DyRTP/releases/download/v1.0.0/DyRTP-v1.0.0.jar), SHA-256 `F66C39A0309885306798AEBFFCDF4785576F422DE2C77DAA25843360D5929487`, was tested separately with economy off, radius 500–1000, warmup 3 and Paper chunk-unload delay zero. It found a location and showed warmup, then returned the failed-safe message; the probe recorded no arrival and one timeout. Replacing it with the 1.0.1 artifact under the same configuration produced one confirmed arrival, zero basic check failures and 3503.662 ms command-to-event time. This controlled sample reproduces the warmup/chunk-retention regression; it is not a population performance estimate. These two control requests did not use reset world snapshots or identical random destinations.

## Evidence and limits

Probe/build/settings/client source: [scripts/comparison](../scripts/comparison/README.md). Raw logs, settings and client messages are in `C:\Users\alph\AppData\Local\Temp\DyRTP-BetterRTP-comparison-20261023`:

- `dyrtp-comparison.log`, `betterrtp-comparison.log`, `dyrtp-client.jsonl`, `betterrtp-valid-client.jsonl`.
- `dyrtp-small-config.yml`, `dyrtp-large-config.yml`, `betterrtp-small-config.yml`, `betterrtp-large-config.yml` and the unchanged saved `snapshot/benchmark` baseline.
- `betterrtp-invalid-settings-attempt.log`, excluded from timing results.
- `dyrtp-live-complete.log`, `dyrtp-live-warmup-no-vault.yml`, `paper-world-live-unload0.yml`, `no-provider-price-command.txt`.
- `historical-warmup-unload0.log`, `historical-warmup-client.jsonl`, `candidate-warmup-unload0.log`, `candidate-warmup-client.jsonl`, `controlled-warmup-config.yml`.

One client, a flat Overworld, small samples and random targets were used. Disk caches, JIT, GC, previous requests and uncontrolled shared-host activity affect measurements; light browser/publication preparation overlapped on the host, although no concurrent heavy builds were scheduled during timing. The second pass does not guarantee every target was already loaded. Stock full profiles, concurrent players, real Bedrock/Geyser transport, a real Vault provider, production CPU/heap, queue preparation cost and other Minecraft releases were not compared. These measurements do not verify DyClaim integration; that is covered separately in [verification](verification.md). No universal speed, safety or compatibility guarantee is made.
