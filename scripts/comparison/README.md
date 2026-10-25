# Disposable RTP comparison probe

Build with `gradle -p scripts/comparison clean jar`. This separate probe is not part of the DyRTP distribution. Run only in disposable worlds, using one RTP plugin at a time, with an actual client named `DyRtpReview`.

`rtpcompare <dyrtp|betterrtp> <world> <count> <label>` dispatches that plugin's namespaced normal command sequentially. It temporarily grants player-use/world permissions, measures command-to-teleport-event time, confirms the actual position on the next tick and logs median/p95/max, basic safety failures and chunk events. It waits at most 10 seconds for each successful teleport and stops after three missing teleports. Counts are limited to 100.

`configure.py small|large <temporary-folder>` writes benchmark settings to the explicitly named temporary comparison folder. It uses only Python standard-library text transformations of original configs. This script intentionally changes test settings; never point it at production. Save the resulting settings with each run and restart/reload the appropriate plugin before measuring.

Restore an identical saved world before the second plugin phase. Do not run overlapping measurements or change configuration during a run. Queue-enabled BetterRTP should have a separate labelled run and a recorded preparation window.

Basic floor/headroom checks and server-wide chunk counters are not exhaustive safety, CPU, heap or network benchmarks. DyRTP's separate DyClaim integration checks belong to the integration probe, not this comparison.

`python scripts/comparison/render_chart.py` renders the five final timing rows from `docs/betterrtp-comparison.md` as a 2000×1680 PNG and editable SVG in `docs/images/`. It requires Pillow only in the graphics runtime, adds no product dependency and does not rerun the benchmark. The shared bar scale is linear, starting at zero; displayed median values are rounded to two decimal places.

The 2026-10-23 run used Paper 26.1.2 build 74 and minecraft-protocol 1.68.0 (protocol 775), not Paper 26.3. `client.js` connects the real non-OP `DyRtpReview` client to loopback port 25579, acknowledges destination packets and records chat/position evidence. Set `RTP_CLIENT_MODULES` to the disposable npm node_modules folder and `RTP_CLIENT_LOG` to a disposable output path. Type player commands on its stdin, or `quit` to disconnect. The probe refuses OP players; `rtpcompare worlds` prints the actual Bukkit world names. The settings writer's WorldType entries match the run's benchmark/benchmark_nether/benchmark_the_end world names.

`results-20261023.json` contains the final aggregate rows and SHA-256 artifact identifiers. Invalid setup attempts and controlled warmup regression checks are excluded from those 70-request timing rows. One BetterRTP Nether landing failed the shared basic predicate; its coordinates/cause were not logged. The figure states this limitation instead of claiming universal safety. See the report for the exact evidence paths.

`bench.cjs` repeats the whole two-plugin run automatically (fresh snapshot copy per plugin, same phase order and settings files); it expects the 2026-10-23 workspace in the temporary folder named inside it. `java scripts/comparison/RenderChart.java .` renders the same chart without Python or Pillow. `results-20261024.json` holds the 1.1.0 rows.
