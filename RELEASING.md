# Releasing

The three modules publish to Maven Central through Sonatype's Central Publisher Portal,
using the `com.gradleup.nmcp.settings` plugin configured in `settings.gradle.kts`. One
upload carries every module and every target.

## Prerequisites

Before the first release, make sure all of the following are true:

- the `io.liquidsoftware` namespace is verified in your account at `https://central.sonatype.com`
- you have generated a Central Portal user token
- you have a working GPG key for signing artifacts
- your public GPG key has been uploaded to a public keyserver

Central docs:

- `https://central.sonatype.org/publish/generate-portal-token/`
- `https://central.sonatype.org/publish/requirements/`
- `https://central.sonatype.org/publish/requirements/gpg/`

## Credentials

Create or update `~/.gradle/gradle.properties`:

```properties
centralPortalUsername=...
centralPortalPassword=...
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=...
```

Signing is only required for the Central upload, so `publishToMavenLocal` works without
a key.

## Release steps

Release from a Mac, which can build and test every target.

1. Ensure the working tree is clean and on `main`.
2. Move the `Unreleased` entries in `CHANGELOG.md` under the new version.
3. Set `version` in `build-logic/src/main/kotlin/impossible.kotlin-library.gradle.kts`.
4. Run a full local verification build, then check the published files:

   ```bash
   ./gradlew clean build publishToMavenLocal
   scripts/check-publication.sh X.Y.Z
   ```

5. Tag the release:

   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

6. Upload the release to Central:

   ```bash
   ./gradlew publishAggregationToCentralPortal
   ```

Publication is automatic by default: if the upload validates, Sonatype releases it. To
upload and validate without releasing, pass `USER_MANAGED`, then release or drop the
deployment at `https://central.sonatype.com/publishing/deployments`:

```bash
./gradlew publishAggregationToCentralPortal -PcentralPortalPublishingType=USER_MANAGED
```

To inspect the signed bundle without uploading anything, run `./gradlew nmcpZipAggregation`
and look at `build/nmcp/zip/aggregation.zip`.

## What gets published

Each of `impossible`, `impossible-serialization` and `impossible-testing` publishes a root
artifact with the Gradle module metadata and one artifact per target (`-jvm`, `-js`,
`-wasm-js`, `-linuxx64`, `-macosarm64`, `-iosarm64`, `-iossimulatorarm64`), each with
a sources jar and a javadoc jar holding the Dokka HTML docs.
