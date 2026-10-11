# Disposable RTP comparison probe

Build with `gradle -p scripts/comparison clean jar`. This separate probe is not part of the DyRTP distribution. Run only in disposable worlds, using one RTP plugin at a time, with an actual client named `DyRtpReview`.

`rtpcompare <dyrtp|betterrtp> <world> <count> <label>` dispatches that plugin's namespaced normal command sequentially. It temporarily grants player-use/world permissions, measures command-to-teleport-event time, confirms the actual position on the next tick and logs median/p95/max, basic safety failures and chunk events. It waits at most 10 seconds for each successful teleport and stops after three missing teleports. Counts are limited to 100.

`configure.py small|large` writes benchmark settings to the explicitly named temporary comparison folder. It uses only Python standard-library text transformations of original configs. This script intentionally changes test settings; never point it at production. Save the resulting settings with each run and restart/reload the appropriate plugin before measuring.

Restore an identical saved world before the second plugin phase. Do not run overlapping measurements or change configuration during a run. Queue-enabled BetterRTP should have a separate labelled run and a recorded preparation window.

Basic floor/headroom checks and server-wide chunk counters are not exhaustive safety, CPU, heap or network benchmarks. DyRTP's separate DyClaim integration checks belong to the integration probe, not this comparison.

`python scripts/comparison/render_chart.py` renders the five final timing rows from `docs/betterrtp-comparison.md` as a 2000×1680 PNG and editable SVG in `docs/images/`. It requires Pillow only in the graphics runtime, adds no product dependency and does not rerun the benchmark. The shared bar scale is linear, starting at zero; displayed median values are rounded to two decimal places.
