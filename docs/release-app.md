# Better Bees Release App

The private release App authenticates the final Git push in the Release workflow.
It has no server or webhook. It is installed only on `TheCoolestPaul/Better-Bees`
with Contents read/write and mandatory Metadata read access.

## Setup

1. Under the owner's GitHub **Settings > Developer settings > GitHub Apps**, create
   **Better Bees Release** (use an account-prefixed name if unavailable).
   Set the homepage to the repository URL, disable webhooks, leave OAuth and
   device authorization disabled, and allow installation only on this account.
2. Grant only repository **Contents: Read and write**. Install it on the selected
   **Better-Bees** repository, not all repositories.
3. Create the repository Actions environment **release**. Select deployment
   branches/tags and allow only the **main branch**, with no tag rule and no
   required reviewers. This preserves the one-click release flow.
4. Add environment variable `RELEASE_APP_CLIENT_ID` from the App settings.
   Generate a private key and store the entire PEM as environment secret
   `RELEASE_APP_PRIVATE_KEY`. Do not store it in this repository, logs, or chat.
5. Add the installed App to **Main Protect > Bypass list > Always allow**.
   Preserve existing rules and bypass entries. This exception applies to the
   App identity and the entire ruleset; GitHub does not limit it to version edits.

The workflow pins the official `actions/create-github-app-token` action, requests
only Contents write for this repository, and generates the token in the publishing
job after tests and packaging. The action revokes the token on job completion.
Git uses the App token; GitHub Release uploads use `GITHUB_TOKEN`, and Modrinth
continues to use `MODRINTH_TOKEN`.

## Release behavior

Start **Actions > Release > Run workflow** on **main**. The default is `patch`;
minor, major, current, and custom remain available. Bumps use the greater of
`mod_version` and the last published release. No manual version edit is needed.

Validation and packaging use the selected version. Only after they pass does
publication create a commit changing `mod_version` in `gradle.properties`, if
needed, and an annotated tag. Both refs are pushed atomically, without force.
The script rejects tracked local changes and any unexpected release tree.
The App's push triggers ordinary CI; it cannot recursively start a release
because Release is manual-only.

If main advances before new publication, the run stops; start a new run to
validate the updated source. Rejected atomic pushes update neither main nor tag.

## Recovery

If Git publication succeeded but later publication failed, use **Re-run failed
jobs** to keep the original resolved version and source. The script reuses an
existing tag only when its tree matches the validated source plus the selected
version, and it identifies that source or its direct version-only child. The
tagged commit must remain on main. It never moves tags or force-pushes.

For a new recovery run, choose `custom` with the exact version. This succeeds
only while the newly captured source still matches that release; if main has
advanced, use the original run's failed-job retry and retained artifacts instead.
Do not select `patch` to recover an existing release: it may choose a new version.
Existing Modrinth checksum/conflict checks remain in effect.

## Maintenance and rollback

To rotate the key, generate a second App key, replace the environment secret,
verify authentication, then revoke the old key. To disable release access,
remove the App's ruleset bypass and suspend/uninstall its repository installation.
Do not disable main protection. Removing the environment secret stops new
publishing tokens; existing tokens remain valid until revoked or expired.

Merge workflow changes through the normal PR process after App setup. Validate
the next intentionally requested release; setup must not publish a test release.
