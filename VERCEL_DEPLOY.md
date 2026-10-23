# Mousike 3.0 — Premium Glass Edition: Vercel deployment

**Build:** FastAPI + vanilla HTML/CSS/JavaScript, with Node.js and FFmpeg binaries bundled through Python wheels. No frontend rebuild, external database, or Node.js build is needed.

## Quick deployment (Git integration)

1. Extract `Mousike_3.0_Premium_Glass_Vercel_Ready.zip`.
2. Upload **the contents of the `Mousike` folder** to a private or public GitHub/GitLab/Bitbucket repository (do not upload `.env` or credentials). Alternatively, set **Root Directory = `Mousike`** when you commit the outer folder to Git.
3. On https://vercel.com/new, choose **Import Git Repository**, select the repo, and use the **FastAPI** framework preset. The project also specifies this in `vercel.json`.
4. Keep **Build Command and Output Directory blank** (framework defaults). Set **Root Directory** correctly. Vercel automatically detects `main.py` and installs `requirements.txt`.
5. Optionally set Vercel environment variables (`Settings → Environment Variables`):
   - `GEMINI_API_KEY` — optional; recommendations work without Gemini. Never put this key in JS or Git.
   - `MOUSIKE_EMBEDDINGS=concept` — optional; this no-download mode is already the default and recommended on serverless.
   - `MOUSIKE_ALLOWED_HOSTS=localhost,127.0.0.1,::1,music.yourdomain.com` — **required if you attach your own custom domain**; auto-allowed for standard `*.vercel.app` preview/production URLs.
   - Do **not** set `MOUSIKE_HOST` or `MOUSIKE_PORT` on Vercel; these are local-only.
6. Click **Deploy**. In Vercel's overview, open the deployed website and check `/api/health` for `{"ok":true,"service":"Mousike"}`.
7. Test onboarding, home, search, playback, album tracks, library, and settings **on the real deployed URL**. Audio extraction may be limited by YouTube even if the deployment itself succeeds.

## Deploy directly from your PC (no Git)

Install Node.js 22+ and run, from *inside* the unzipped `Mousike` folder:

```bash
npm install -g vercel
vercel login
vercel --prod
```

When prompted, choose to create a new project and use the current directory. Do not enter an additional build command or output directory. Visit the URL returned by Vercel.

## What this edition changed

- `vercel.json`: FastAPI preset, Fluid Compute, 300-second maximum function duration, public asset cache headers.
- `pyproject.toml`: sets `main:app` explicitly and promotes public `/assets` from the existing mount to Vercel CDN, bypassing the API middleware **only for public static files**.
- `requirements.txt`: adds `nodejs-wheel` (Node 24) for yt-dlp's JavaScript challenge solver and `imageio-ffmpeg` for timestamped audio exports. Both are installed as Python dependencies at build time.
- `runtime_support.py`: selects the actual Node binary and FFmpeg executable, whether running locally or in Vercel's Linux environment.
- `security.py`: allows Vercel-generated `*.vercel.app` deployment URLs, uses the actual HTTPS scheme behind Vercel's trusted reverse proxy, and still blocks other cross-origin API requests and unconfigured custom domains.
- `.vercelignore`: excludes local tests and development artifacts from upload.
- All existing HTML, CSS, JavaScript, branding, user profiles, recommendation logic, player controls, and API route names remain unchanged.

## Important limitations

**Vercel hosting does not guarantee functioning YouTube extraction or playback.** yt-dlp may be blocked or challenged from datacenter IPs, access rules change, and some videos require authentication. We do not bypass restrictions. Use media only where you have the necessary rights and where provider terms permit it. Search, extraction, and audio playback require network access to the media provider.

**Streaming and export requests remain bounded by serverless lifetimes.** A long track, unusually slow extraction, an upstream stall, or a large file can exceed function duration. A browser range retry may help, but the original player remains best-effort. FFmpeg chapter exports run in temporary storage, only for the duration of the request; long exports may time out. A dedicated media service is recommended for large-scale continuous playback.

**User taste and Gemini key settings remain browser-owned** unless you explicitly set the optional server-side `GEMINI_API_KEY` in Vercel. Browser localStorage isn't shared across devices. In-memory search and AI caches are per-function-instance and may reset on cold starts.

The heavyweight neural `sentence-transformers` embedding extra is intentionally **not bundled**; the original multilingual concept/semantic fallback still works. Installing `requirements-embeddings.txt` would significantly increase deployment size and start time.

The app's rate limits are in-process, not distributed across function instances. For a public high-traffic launch, add centralized abuse prevention/rate limiting and a production-grade licensed audio source.

## Troubleshooting

| Symptom | Check |
|---|---|
| `400 Host is not allowed` | For a custom domain, set `MOUSIKE_ALLOWED_HOSTS` to include that domain, then redeploy. Standard `*.vercel.app` is automatic. |
| `403 Cross-origin request blocked` | Access the frontend and API using the **same** HTTPS origin; check reverse-proxy forwarded protocol. |
| Site loads but no albums or songs | Open `/api/health`, then `/api/search?q=hello` and check Vercel Function Logs; yt-dlp may be blocked by YouTube. |
| Missing JS challenge solver | Check `/api/capabilities` → `js_runtime_available:true` and that `yt-dlp[default]` and `nodejs-wheel` installed successfully. |
| Chapter export unavailable | Check `/api/capabilities` → `ffmpeg:true`; the wheel must be installed. |
| Timeout / 504 | Check source latency and Vercel Function Logs. Functions are limited; use a dedicated streaming backend for lengthy playback. |
| Custom domain not working | Add the domain in Vercel **and** `MOUSIKE_ALLOWED_HOSTS`. |
| `FUNCTION_INVOCATION_FAILED` | Look at Vercel Functions logs for Python dependency/build/runtime errors. |

## Local testing

```bash
python -m pip install -r requirements.txt
python main.py
```

Open http://127.0.0.1:8000 and visit `/api/health`. Local development works exactly as before. `start.bat` and `start.sh` are still present.

## Reference documentation

- [Vercel FastAPI](https://vercel.com/docs/frameworks/backend/fastapi)
- [Vercel Python Functions](https://vercel.com/docs/functions/runtimes/python)
- [Vercel Function limits](https://vercel.com/docs/functions/limitations)
- [yt-dlp external JS runtimes](https://github.com/yt-dlp/yt-dlp/wiki/EJS)
