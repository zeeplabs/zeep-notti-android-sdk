# Releasing

How Julio releases `com.zeeplabs:notti-android` to Maven Central (AD-015). The agent prepares the release PR, CHANGELOG, this runbook and the device checklist; Julio creates and pushes the tag, runs or watches the upload and presses Publish. A version on Maven Central can never be replaced or deleted, so the Portal review is the last gate.

Two equivalent upload paths:

- **CI:** pushing a `X.Y.Z` tag runs `.github/workflows/release.yml` (full CI, then the upload).
- **Local fallback:** the same Gradle task from Julio's machine, when the CI path is not available.

Both upload a signed deployment **without releasing it** (`publishToMavenCentral`, never `publishAndReleaseToMavenCentral`).

Never put a token, password, key or key id in this file, in a PR, in a commit or in a chat. Values below are placeholders.

## One-time setup

### 1. Namespace `com.zeeplabs` (B-001)

1. Sign in at <https://central.sonatype.com> with the account that will own the namespace.
2. **Namespaces → Add namespace** → `com.zeeplabs`. The Portal shows a verification key.
3. Add that key as a DNS **TXT** record on the domain that matches the namespace (the reversed domain of `com.zeeplabs`: `zeeplabs.com`). Confirm the domain is ours before this step; if it is not, the group id must change before 0.1.0 (a new decision in `STATE.md`).
4. Back in the Portal, **Verify namespace**. Wait until it shows **Verified**. The TXT record can be removed afterwards.
5. Close B-001 in `.specs/project/STATE.md`.

### 2. Portal user token

1. In the Portal: **View Account → Generate User Token**.
2. Keep the two values it shows (token username and token password) in the password manager. They are the `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` below. The account password itself is never used by the build.

### 3. Signing key

On the release machine (`brew install gnupg` on macOS):

```sh
gpg --full-generate-key
#   kind: RSA and RSA, size: 4096, expiry: your choice (e.g. 2y)
#   name: Zeep Tecnologia, email: an address that receives mail
#   passphrase: required; keep it in the password manager
gpg --list-secret-keys --keyid-format long     # note the key id after "rsa4096/" (KEY_ID below)
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
gpg --keyserver keys.openpgp.org --send-keys KEY_ID
```

Central checks signatures against public keyservers: wait a few minutes after `--send-keys` and confirm the key is found with `gpg --keyserver keyserver.ubuntu.com --recv-keys KEY_ID` on another machine or a fresh `GNUPGHOME`. keys.openpgp.org also sends a verification mail to the key's address; confirm it.

Back up the secret key (`gpg --armor --export-secret-keys KEY_ID > notti-signing.asc`) to the password manager or an encrypted vault, then delete the file. Never commit it: `*.gpg` and `secring.*` are git-ignored, an exported `.asc` is not.

### 4. GitHub environment and secrets (CI path)

1. Repository **Settings → Environments → New environment** → `maven-central`.
2. Optional: add Julio as a **required reviewer**, so every upload waits for an approval.
3. Optional: **Deployment branches and tags** → allow tags matching `[0-9]*.[0-9]*.[0-9]*` only.
4. Add four **environment secrets** (not repository secrets):

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Portal token password |
| `SIGNING_KEY` | output of `gpg --armor --export-secret-keys KEY_ID` (the whole block, including the `BEGIN`/`END` lines) |
| `SIGNING_KEY_PASSWORD` | the key passphrase |

The upload job fails naming any empty secret, without printing values.

### 5. Local fallback credentials

In `~/.gradle/gradle.properties` (outside the repository, `chmod 600`):

```properties
mavenCentralUsername=PORTAL_TOKEN_USERNAME
mavenCentralPassword=PORTAL_TOKEN_PASSWORD
```

The key is not stored there: it is exported into the shell for the upload only (step 6 of the release).

## Release steps

### Before the tag

1. The [device checklist](DEVICE_CHECKLIST.md) is run and recorded; every Fail is fixed or listed in the README's known limitations.
2. Play Data safety wording in the README confirmed (AD-011). Proposed ADs confirmed (AD-005, AD-008..AD-011).
3. B-001 closed (namespace verified). Release path chosen: CI or the local fallback.
4. In the release PR: date the CHANGELOG section (`## [0.1.0] - YYYY-MM-DD`) and drop the README banner's "Until the release is published" sentence. `scripts/check-version.sh` checks both on the tag build.
5. Run the Full gate (`.specs/codebase/TESTING.md`), including `scripts/check-release-artifacts.sh`. Merge the PR into `main`.

### Tag

```sh
git checkout main && git pull --ff-only
GITHUB_REF=refs/tags/0.1.0 scripts/check-version.sh   # the tag-build checks, before tagging
git tag -a 0.1.0 -m "0.1.0"
git push origin 0.1.0
```

Tags are `X.Y.Z`, no `v`. `release.yml` ignores any other tag.

### Upload: CI path

Watch **Actions → Release** for the tag. The `ci` job runs the whole CI (with the tag checks); then `upload` (in `maven-central`, after approval if a reviewer is set) runs `:notti:publishToMavenCentral`. Go to [Portal review](#portal-review-and-publish) when it is green.

### Upload: local fallback

From a clean checkout of the tag:

```sh
git checkout 0.1.0 && git status --short          # must be empty
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
GITHUB_REF=refs/tags/0.1.0 scripts/check-version.sh
# Full gate from TESTING.md here.
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys KEY_ID)"
read -rs ORG_GRADLE_PROJECT_signingInMemoryKeyPassword && export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
./gradlew --console=plain :notti:publishToMavenCentral
unset ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

Without `signingInMemoryKey` the build stops before any upload ("refusing an unsigned upload"). Do not run this in a shell that records history to a shared place, and close the terminal afterwards.

### Portal review and Publish

1. Portal → **Publish → Deployments**. The new deployment for `com.zeeplabs:notti-android:0.1.0` goes through **Validating** to **Validated**.
2. Open it and check: the group, artifact and version; the files (`.aar`, `.pom`, `-sources.jar`, `-javadoc.jar`, `.module`, each with `.asc` and checksums); no validation error.
3. If anything is wrong: **Drop**. Nothing was released; fix, and upload again (same version is fine while nothing was published).
4. If it is right: **Publish**. The deployment goes to **Publishing** and then **Published**. The artifact reaches `repo1.maven.org` within about 30 minutes, search later.
5. Check resolution from a clean project: `implementation("com.zeeplabs:notti-android:0.1.0")` with `mavenCentral()`.

### GitHub Release

```sh
gh release create 0.1.0 --title "0.1.0" --notes-file <(sed -n '/^## \[0.1.0\]/,/^## \[/p' CHANGELOG.md | sed '$d')
```

Attach nothing: the artifacts live on Maven Central.

### After the release

1. `.specs/project/STATE.md`: current work, the release date and commit, closed blockers and the device-checklist todos that passed (EXR-13).
2. `CHANGELOG.md`: keep an empty `## [Unreleased]` above `## [0.1.0]`.
3. The next version starts by bumping `NottiSdkVersion.CURRENT` with its CHANGELOG section (`TBD`).
4. Vault and Cortex: release noted.

## Failure handling

- **Upload job fails on secrets:** the log names the empty secret; fix it in the `maven-central` environment and re-run the job.
- **Portal validation fails** (missing signature, POM field, unknown key): drop the deployment, fix, re-run the upload. For an unknown key, check the keyserver upload (setup step 3).
- **A wrong version was published:** it cannot be replaced or deleted. Fix forward: bump the patch (`0.1.1`), document it in the CHANGELOG, release again.
- **The tag points at the wrong commit and nothing was published:** delete it (`git push --delete origin 0.1.0 && git tag -d 0.1.0`), drop any deployment, tag again.
- **Key compromised:** revoke it (`gpg --gen-revoke KEY_ID`, import, `--send-keys`), generate a new key, update the secrets.
