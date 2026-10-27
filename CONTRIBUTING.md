# Contributing to Mousiké

Maintainer: **Sujal Wagholikar** · https://github.com/sujalwagholikar/mousike

## Before changing code

1. Read the platform README, LICENSING.md, SECURITY.md and CODE_OF_CONDUCT.md.
2. Search existing issues. For a substantial behavior change, explain the problem, affected platform and expected outcome first.
3. Fork or create a branch from the current intended base. Do not edit generated build outputs or include real keys/user data.
4. Keep Windows, Android and showcase changes clearly identified; the website demo is not the installed app engine.

## Implementation and review

Use small changes with a clear trigger and before/after behavior. Preserve music/video identifiers, evidence-based timing and source-expiry/cancellation rules unless the task explicitly changes them. Avoid broad playback-engine rewrites for a visual-only request. Keep artwork proportions, keyboard access, responsive layout and reduced-motion support intact.

Run checks appropriate to the change. A focused bug fix needs a meaningful regression check when practical; documentation and simple reversible styling do not need unrelated full device suites. For playback changes, exercise rapid selection, pause/resume, next/previous, seeking, offline playback and failures on the affected platform. State exactly what you could not test.

A pull request should include: the problem and resulting behavior; platforms/files changed; relevant validation; screenshots for material UI changes using non-sensitive data; dependencies/license changes; and data-migration or rollback implications. Do not include misleading benchmark or production-readiness claims.

## Rights and dependencies

Submit only work you are authorized to contribute under the applicable project/component license. Keep third-party notices and distinguish copied code from original work. Contributors retain their copyrights unless a separate valid agreement provides otherwise. No copyright assignment or new CLA is created by this guide.

Before adding a dependency, document its source, version, license, runtime/network behavior and why it is needed. Retain license text and assess redistribution/source obligations. Do not assume every font, icon, image or bundled executable uses the application's license.

Security reports belong in the private process described by SECURITY.md, not in a public reproduction PR containing exploitable details.
