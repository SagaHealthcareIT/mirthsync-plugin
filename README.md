# MirthSync Plugin for Mirth Connect

This repository contains Saga-IT's **MirthSync** plugin for [Mirth / NextGen Connect](https://github.com/nextgenhealthcare/connect).  
The plugin embeds the [`mirthsync`](https://github.com/SagaHealthcareIT/mirthsync) CLI so administrators can pull, push, and inspect channel configuration from within the Mirth Administrator UI or directly on the server.

Key capabilities:

- Unified **server and client execution**: run mirthsync either on the Mirth server or locally inside the Administrator UI.
- **Session-aware authentication**: automatically reuses the current user's `JSESSIONID` when no username/password is provided; tokens/passwords are never persisted.
- **Opinionated presets** for common pull/push and Git actions (both server and client targets) with sane defaults such as `/api/<MIRTH_VERSION>` URLs.
- Built-in **Git helpers** (status, log, diff, etc.) that understand Mirth's constraints (e.g., color disabled, helpful error messages when repos are absent).
- **Signed jars** and packaged dependencies ready for installation (`mirthsync-plugin.zip`).
- **GitHub Actions** workflow to build on every push/PR and publish tagged releases automatically.

---

## Repository Layout

```
├── client/    # Swing UI & Administrator integration
├── server/    # Servlet + REST endpoints invoked by Mirth
├── shared/    # Constants, DTOs, and API interfaces shared by client/server
├── libs/      # Runtime/compile-time dependency stubs (populated by build)
├── certificate/keystore.jks  # Self-signed cert used for jarsigning
├── build.sh   # Orchestrates Maven build, signs jars, assembles plugin zip
└── .github/workflows/build-release.yml  # CI/CD workflow
```

Each Maven module produces one jar (`mirthsync-plugin-{server|shared|client}.jar`) which is bundled and signed during the build.  
External runtime libraries (including mirthsync) are copied to `mirthsync-plugin/libs/` and referenced from `plugin.xml`.

---

## Prerequisites

- **Java 17** (Temurin/Adoptium recommended)
- **Maven 3.9+**
- **Git**
- Access to a Mirth Connect 4.5.x environment
- Optional: custom signing certificate/keystore (default self-signed cert lives in `certificate/keystore.jks`; password is `storepass` and alias `selfsigned`)

The plugin depends on [`com.saga-it:mirthsync`](https://clojars.org/com.saga-it/mirthsync), which is published on [Clojars](https://clojars.org) and resolved automatically during the Maven build.

---

## Building Locally

```bash
git clone https://github.com/SagaHealthcareIT/mirthsync-plugin.git
cd mirthsync-plugin
./build.sh
```

What the script does:

1. Runs `mvn install package` across the multi-module project.
2. Copies mirthsync + runtime dependencies into `mirthsync-plugin/libs/`.
3. Signs any unsigned shared libs plus the three module jars using `certificate/keystore.jks`.
4. Generates `plugin.xml` via the `mirth-plugin-maven-plugin`.
5. Packages everything into `mirthsync-plugin.zip` in the repo root.

> Note: the script prints repeated `jansi ... is not executable` warnings on GitHub-hosted runners; these are harmless.

---

## Installing into Mirth Connect

1. **Stop** Mirth Connect.
2. Copy `mirthsync-plugin.zip` into `<mirth-install>/custom-plugins/`.
3. **Start** Mirth Connect. The plugin will be extracted and jars validated.
4. Launch the Administrator and navigate to **Settings → MirthSync** to configure.

To upgrade, remove the previous `mirthsync-plugin` directory under `<mirth-install>/custom-plugins/` and drop in the new zip before restarting.

---

## Using the Plugin

From **Settings → MirthSync** inside the Administrator UI:

- **Connection**  
  - Server URL automatically appends `/api/<PlatformUI.SERVER_VERSION>` (e.g., `https://localhost:8443/api/4.5.2`).  
  - Username/password fields are optional; if blank, the servlet injects the current session token.

- **Execution Mode**  
  - *Run on server*: executes mirthsync beans on the Mirth server.  
  - *Run locally*: executes mirthsync inside the Administrator JVM (target directories can be browsed via file chooser).  
  - For Git actions, connection inputs are disabled because they are not required.

- **Presets**  
  - Default presets cover server and client pulls, Git status/log/diff (working tree & staged).  
  - URLs always include the correct `/api/<version>` suffix.  
  - Users can save/delete custom presets; names are shared across all users (via plugin properties).

- **Flags & Toggles**  
  - Includes mirthsync options like `--force`, `--include-configuration-map`, `--git-init`, `--delete-orphaned`, etc.  
  - “Allow interactive prompts” is currently disabled with a tooltip (“Currently unsupported”).

- **Git Metadata & Commands**  
  - Git author/email pre-filled from the current Mirth user and cannot be toggled read-only (per design).  
  - Git command field supports arbitrary sub-commands (e.g., `diff --cached`, `log -n 5`). Color/pager flags are automatically suppressed.

- **Output Panel**  
  - ANSI escape codes and non-XML-safe characters are stripped to keep Mirth’s serializer happy.  
  - Git-style syntax highlighting is applied (adds/removes/hunks/headers), and you can pop the output into a detached window.  
  - Session tokens/passwords are redacted everywhere.

---

## Continuous Integration / Releases

- `.github/workflows/build-release.yml` builds on every push and pull request targeting `main`.
- On tagged pushes matching `v*`, the workflow:
  - Rebuilds the plugin using `build.sh`
  - Uploads `mirthsync-plugin.zip` and a SHA-256 checksum
  - Publishes both assets to the GitHub Release associated with the tag

To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The GitHub Action will attach the installable zip to that release automatically.

---

## Development Notes

- Maven property `mirth.plugin.path` (default `mirthsync-plugin`) defines the staging folder for assembled artifacts.
- The build script signs jars with the self-signed keystore in `certificate/` for local development. Official release signing is handled externally after the CI build.
- When adding dependencies that must ship with the plugin (e.g., new clojure libs), drop them into `libs/runtime/{client|shared}` so the build script can bundle and sign them.
- Remember that Mirth’s Jersey stack serializes via XStream/MOXy. Avoid immutable Java collections or custom DTOs unless you register converters; we stick to primitives and `ArrayList`/`LinkedHashMap`.

---

## About & Commercial Support

The MirthSync plugin is built and maintained by [Saga IT, LLC](https://saga-it.com). For hands-on help:

- [Mirth Connect consulting & development](https://saga-it.com/services/mirth-connect)
- [Open Integration Engine (OIE) services](https://saga-it.com/services/open-integration-engine)
- [mirthsync CLI](https://github.com/SagaHealthcareIT/mirthsync) and the [VS Code extension](https://saga-it.com/products/mirthsync-vscode)

---

## Contributing

Issues and pull requests are welcome!  
When submitting changes:

1. Run `./build.sh` and ensure `mirthsync-plugin.zip` is produced without errors.
2. Include details on how the change impacts the client/server modules.
3. If applicable, update this README, presets, or build/release automation.

For significant features, open an issue first so we can align on expectations.  
Thanks for helping improve the MirthSync plugin!
