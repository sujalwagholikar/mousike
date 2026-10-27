# Release and deployment procedure

Release maintainer: **Sujal Wagholikar**.

## 1. Freeze and identify

1. Choose a release commit for each platform. Record versions, architecture, dependency lockfiles and build commands.
2. Resolve the existing mismatch between an Android binary filename and source version by inspecting the binary's metadata, not guessing from the name. Renaming is not rebuilding.
3. Build from the recorded source. Keep secrets, personal data, caches and generated dependency folders out of source archives.
4. Review LICENSE.md, component notices, source-distribution obligations and the privacy endpoint inventory.

## 2. Validate and sign

1. Run the focused core/transport checks appropriate to the changes.
2. Test installed Android/Windows playback, rapid switching, source failures, notifications/media controls, queue, offline audio and settings persistence.
3. Check website desktop/mobile layouts, screenshot motion, reduced motion, keyboard access and actual download targets.
4. Test Windows install/uninstall, DPI, tray and secure-storage behavior on Windows. Previous browser audio checks are not equivalent.
5. Use a controlled production signing identity where available. Never call a public development keystore a private production key, and never describe an unsigned EXE as signed.
6. Keep signing credentials outside the repository with restricted access and separate recoverable backups. Changing Android signing identity can affect update compatibility.

## 3. Verify the artifacts

Generate hashes in PowerShell:

```powershell
Get-FileHash .\downloads\Mousike-3.6.0-Windows-x64-Setup.exe -Algorithm SHA256
Get-FileHash .\downloads\Mousike-3.5.1-arm64.apk -Algorithm SHA256
```

Verify every final ZIP can be read and contains the expected source/assets. Exclude interrupted copy files and temporary outputs. Build installers to unique temporary filenames, then install/rename the verified final result, avoiding concurrent writers to the same output. Confirm hashes after copying/uploading, not only before packaging.

## 4. Publish source and binaries

1. Commit source, documentation, lockfiles and required notices to the repository.
2. Keep large EXEs/APKs out of ordinary Git commits; use release assets or appropriate file storage. The supplied Windows EXE exceeds GitHub's ordinary 100 MiB per-file limit.
3. Tag the intended source revision and create a release with the actual binaries, matching source archives, SHA-256 values and clear platform requirements.
4. Provide the corresponding source and required material for applicable GPL components using a compliant distribution method. Merely attaching app-specific JS/Kotlin while omitting required GPL component source arrangements is not automatically sufficient.
5. Describe what was tested, what remains pending, signing status and known limitations. Do not claim guarantees based solely on unit tests.

## 5. Update the showcase

Update the EXE/APK links in index.html, main.html and README to their actual release asset URLs. Verify both filename and architecture. Upload the static website to a host that supports the selected assets/file sizes. Check the production page over HTTPS, not only the local file preview.

## 6. Maintain and roll back

Retain prior source, checksums and release notes. If a binary is defective, publish a clear correction and replacement rather than silently changing the source/binary association. Identify affected releases. For a security incident, coordinate through SECURITY.md and rotate exposed credentials. An automatic updater or security backport service is not established by this guide.
