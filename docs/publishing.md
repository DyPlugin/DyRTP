# DyRTP publishing conventions

## Project presentation

- Project name: `DyRTP`.
- Public summary: Lightweight random teleportation with safety checks, DyClaim integration, and EN/TR support.
- Keep the project's established `Made Simple` heading, three short tagline sentences and introductory paragraphs.
- Follow with `New in 1.0.0`, `Choose your server build`, `Why DyRTP?`, feature sections, commands, setup and source/support links where applicable.
- Preserve each project's actual features, optional dependencies, license and verification limits. A shared style does not imply identical capabilities or support.
- Use the same summary and current-version description on GitHub, Modrinth, CurseForge, Hangar and SpigotMC, adapting only the platform's formatting and length limit.

## Versions and commits

- Release display title: `DyRTP 1.0.0`. Use the same pattern for historical display titles, retaining `-BETA` for beta releases.
- New stable tag: `1.0.0`. New standard artifact: `DyRTP-1.0.0.jar`; retain existing names of already published files.
- Release commit: `Release DyRTP 1.0.0`.
- Description-only commit: `docs: align DyRTP project presentation`.
- Release-note heading: `# DyRTP 1.0.0`. Use short feature/change, verification and upgrade sections as applicable.
- Never rename an existing tag, rewrite a historical commit or replace a published JAR merely to change presentation. Historical links, dependency references and verification evidence must continue to identify the original artifact.
- Package suffixes describe real builds only: DyClaim's `-Folia` and DyDiscord's `-standalone` are not interchangeable.
- Keep source versions independent: matching the style does not require giving different products the same version number.

This convention changes documentation and release metadata. It does not authorize a new binary release, production installation or changed license terms.
