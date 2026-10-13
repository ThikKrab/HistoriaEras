# Creating your HistoriaEras repository

## What to upload

Extract `HistoriaEras-GitHub-Repository.zip`. The extracted project contains the
actual Java source, resources, all 57 tests, Gradle wrapper/build configuration,
license notices, documentation and a GitHub build workflow. Upload the project
CONTENTS so `README.md`, `build.gradle` and `src/` are at the repository root.
Do not upload the ZIP as the only repository file.

No Git history, real server config, bridge secret, world, player data, local
cache, third-party mod JAR or compiled release is included. The only bundled
JAR is the Gradle wrapper, which is required to build the project.

## Recommended upload method: Git

Create an empty GitHub repository named `HistoriaEras`, with the visibility you
want. A private repository is suitable if you do not want the source publicly
visible. Do not initialize it with another README/license/gitignore, because
this package already contains those files.

Open a terminal in the extracted project folder and run:

```text
git init
git add .
git status
git commit -m "Initial HistoriaEras 0.7.1-alpha source"
git branch -M main
git remote add origin https://github.com/YOUR-ACCOUNT/HistoriaEras.git
git push -u origin main
```

Replace `YOUR-ACCOUNT` with your actual GitHub account or organization. Authenticate
through your normal GitHub/Git credential flow; never put a token in a source
file or in the remote URL. Review `git status` before committing. If build or
runtime folders appear, stop and verify that `.gitignore` is present.

GitHub Desktop is another option: add the extracted folder as a local repository,
review the changes, commit and publish it. Ensure dotfiles and the `.github` folder
are included; they contain important ignore rules and the build workflow.

## After upload

The Actions workflow will compile and run unit tests. Its runner does not have a
graphical Minecraft client or the live server/modpack, so a successful workflow
does not replace in-game testing. The package was also built locally from the
clean source folder; see `docs/REPOSITORY_VERIFICATION.txt`.

Use a GitHub Release for downloadable compiled mod JARs, rather than committing
them into `src/`. You can attach the separately supplied
`historiaeras-0.7.1-alpha.jar`. Do not attach the sources JAR as an installable mod.

The source keeps the All Rights Reserved license. Do not choose an additional
GitHub open-source license unless you intentionally want to change those rights.

This package does not create, push or publish a GitHub repository automatically.
