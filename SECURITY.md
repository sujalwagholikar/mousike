# Security policy

Maintainer: **Sujal Wagholikar**  
Repository: https://github.com/sujalwagholikar/mousike

## Reporting a vulnerability

1. Visit the repository's **Security → Advisories → Report a vulnerability** option if it is available. Private vulnerability reporting must be enabled by the repository owner; this document does not assert it is already enabled.
2. If unavailable, open a public issue containing only: “I need a private channel to report a security vulnerability.” Do not publish exploit instructions, keys, user data or an affected user's identity.
3. Once a private channel is established, provide the affected version/commit, platform, reproduction steps using test data, expected and actual behavior, impact and a minimal proof of concept.
4. State whether the issue is already publicly known. Coordinate a disclosure date and credit preference with the maintainer.
5. Stop testing if you encounter someone else's data. Retain only the minimum evidence necessary and arrange secure deletion after coordination.

No private email address, guaranteed response time, paid bounty, legal safe-harbor grant or third-party testing authorization is promised. Use only systems and accounts you own or are authorized to test. Do not test YouTube, Google or another provider on Mousiké's behalf without that provider's permission.

## Supported versions

Security reports should identify the exact Android, Windows or website revision. The latest published source/release is the primary maintenance target; no automatic update delivery or guaranteed backport schedule is established. The Windows source identifies 3.6.0, while the retained Android download filename may identify an older version. Report the installed app's version and binary hash, not only its filename.

## Reviewed boundaries

- Windows uses an isolated sandboxed renderer, restricted preload/IPC methods, local data storage and a tokenized loopback media server. This is defense in depth, not a security certification.
- Optional Windows Gemini keys are saved using Electron safeStorage/Windows encryption; Android source uses Android Keystore-backed encryption. Keys should not appear in exported libraries or repository files.
- Media resolution invokes bundled native tools. Keep subprocess arguments separate from shell strings; validate IDs, protocols, redirects, hosts, file paths and downloaded content.
- Treat catalog titles, artwork URLs, lyrics, model output and import files as untrusted input. Do not turn AI responses into shell commands or arbitrary navigation.
- A localhost-only endpoint still needs authorization tokens, resource limits and input checks. Do not bind playback endpoints to a public network interface by default.

## Maintainer response procedure

1. Acknowledge privately when practical and reproduce with a separate profile/test account.
2. Classify affected builds, exploit prerequisites and data exposure; preserve minimal evidence.
3. Revoke/rotate exposed credentials through their providers. Removing a key from Git history does not revoke it.
4. Develop a bounded fix and a regression check; audit related code paths.
5. Publish corrected binaries with matching source and checksums; clearly identify replaced assets.
6. Coordinate an advisory and any user notification based on actual facts and applicable obligations.

## Release limitations

A successful build, antivirus scan or unit-test run does not establish that software is secure. The previously supplied Windows build is unsigned and still needs native Windows acceptance testing; do not describe it as independently audited or production-certified. Never distribute the public development signing key as a private production identity. See RELEASE.md.
