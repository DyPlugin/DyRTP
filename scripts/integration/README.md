# Isolated DyClaim integration probe

This optional test plugin is separate from the distributable DyRTP JAR. It uses the real installed DyClaim manager and modifies temporary claims and terrain. Run only in a disposable server/world, never production.

Build DyRTP first, then run `gradle -p scripts/integration clean jar`. Copy the resulting probe, the reviewed DyRTP JAR and your actual DyClaim JAR to the isolated server's plugins folder. Connect a test client named `DyRtpReview`. Use a small RTP profile and disable minimum distance to keep the search bounded.

From console, run `dyrtpprobe`. Expect eight `PROBE PASS` lines and `PROBE COMPLETE`. It checks namespaced command ownership, unclaimed queries without loading chunks, negative chunk coordinates, owner claims rejected and a real search destination claimed before final validation. The probe unclaims its temporary claims after successful checks; a failed probe may leave temporary claims, which is why the world must be disposable.

Run `dyrtpprobe disable` last. It disables the real DyClaim plugin and verifies that final target validation fails safely. An actual subsequent player RTP should fail rather than bypass protection. Stop the server normally. This probe does not test a real Vault economy provider or Geyser/Floodgate transport.

Additional isolated checks: `dyrtpprobe permissions` temporarily tests OP explicit denies and a single player leaf permission, restoring the original OP state and attachment afterward. `dyrtpprobe effects` starts and cancels two warmups to verify existing/replaced blindness; it temporarily changes runtime warmup settings and removes its test effects afterward. Use a fresh disposable test account, as this scenario deliberately modifies potion state.

`dyrtpprobe metrics` performs 20 sequential real searches in the player's current world and validates each final location. It logs median/p95/max search time, average attempts and resident chunk counts. Timings exclude warmup, client/network latency and later background preload completion. This is a bounded local sample, not a competing-plugin or multiplayer load benchmark. Wait for its result before changing world or stopping the server.
