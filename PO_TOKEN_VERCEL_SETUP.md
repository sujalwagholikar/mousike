# Mousike PO Token (BgUtils 2.0.2) — Vercel deployment

This release keeps the original UI, FastAPI routes, recommendations, library and media-player integration. All call sites use `ydl_options()`, which now opts in to `mweb` and the local BgUtils script provider. It does **not** move the backend to Render or require Webshare. Note that this archive was based on the earlier corrected Mousike Vercel snapshot, not the latest GitHub commit.

## Deployment

1. Back up your latest GitHub code. Merge these changes rather than overwriting newer additions. Changed files: `main.py`, `pot_support.py`, `scripts/build_pot.py`, `pyproject.toml`, `requirements.txt`, `.vercelignore`, `.env.example`.
2. Set `MOUSIKE_POT_ENABLED=true` under Vercel → Project → Settings → Environment Variables. Remove old Webshare environment variables / set any proxy-enabled flag to false, and do not deploy the separate Render backend in this build.
3. In Vercel Project Settings → Build and Deployment, ensure any custom **Build Command** override is unset. The FastAPI build script is declared in `[tool.vercel.scripts]` in `pyproject.toml` and should run `python scripts/build_pot.py` after installing dependencies. Check Vercel logs for `[Mousike POT build] provider built successfully`.
4. The build script clones the **pinned** BgUtils 2.0.2 release, runs `npm ci` and `npx tsc`, and leaves compiled JavaScript and its Node modules in `pot-provider/server`. It may hit Vercel build or function size limits: inspect build output and the deployed `/api/pot-status` endpoint.
5. Deploy from GitHub. Open `https://YOUR-VERCEL-DOMAIN/api/pot-status`. Confirm `enabled`, `plugin_installed`, `compiled_script_present`, `node_available`, `ready` are all true. `ready` is a setup indicator only.
6. Try playback. Check Function logs for a GVS PO Token generation message. This release cannot guarantee that Vercel's outbound IP will pass YouTube bot checks. If the script is missing in function output despite a successful build, Vercel omitted its assets; this needs a packaging adjustment (not repeated yt-dlp configuration changes).

## Local development

- `python -m pip install -r requirements.txt`
- `python scripts/build_pot.py` (requires git, Node >=22, npm)
- `python main.py`

## Limitation

BgUtils documents that a generated PO Token is *not* an IP-based login block bypass and that the script method has process overhead under high concurrency. The app's existing API may return errors when YouTube blocks Vercel. The user-visible frontend is unchanged, but production playback remains unverified until a real Vercel test. See https://github.com/Brainicism/bgutil-ytdlp-pot-provider and https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide.
