"""Local-service request boundaries, body limits, rate budgets and safe headers."""

from __future__ import annotations
from collections import OrderedDict
import os
import time
from urllib.parse import urlparse
from starlette.responses import JSONResponse


class SecurityMiddleware:
    def __init__(self, app):
        self.app = app
        self.buckets = OrderedDict()
        self.allowed = {
            name.strip().lower()
            for name in os.getenv(
                "MOUSIKE_ALLOWED_HOSTS", "localhost,127.0.0.1,::1"
            ).split(",")
            if name.strip()
        }
        self.on_vercel = os.getenv("VERCEL") == "1"
        if self.on_vercel:
            for key in ("VERCEL_URL", "VERCEL_BRANCH_URL", "VERCEL_PROJECT_PRODUCTION_URL"):
                name = os.getenv(key, "").strip().lower()
                if name:
                    self.allowed.add(name)

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        headers = {k.decode().lower(): v.decode() for k, v in scope.get("headers", [])}
        host = urlparse("http://" + headers.get("host", "localhost")).hostname
        # Starlette's in-process transport is accepted only for actual test clients.
        testing = (
            host == "testserver" and (scope.get("client") or ("",))[0] == "testclient"
        )

        async def fail(code, detail, extra=None):
            await JSONResponse({"detail": detail}, status_code=code, headers=extra)(
                scope, receive, send
            )

        # Vercel preview deployments use fresh project-specific *.vercel.app hosts.
        # A custom domain must still be listed in MOUSIKE_ALLOWED_HOSTS.
        vercel_preview = bool(
            self.on_vercel and host and host.endswith(".vercel.app")
        )
        if host not in self.allowed and not vercel_preview and not testing:
            return await fail(
                400,
                "Host is not allowed. Configure MOUSIKE_ALLOWED_HOSTS for your own deployment.",
            )
        path, method = scope.get("path", ""), scope["method"]
        if path.startswith("/api/"):
            origin = headers.get("origin", "")
            if origin:
                p = urlparse(origin)
                # Vercel terminates HTTPS before forwarding to the Python ASGI app.
                # Without this, same-origin POSTs would fail on live HTTPS domains.
                public_scheme = (
                    headers.get("x-forwarded-proto", "https").split(",")[0].strip()
                    if self.on_vercel
                    else scope.get("scheme", "http")
                )
                if p.netloc.lower() != headers.get("host", "").lower() or p.scheme != public_scheme:
                    return await fail(403, "Cross-origin request blocked")
            if headers.get("sec-fetch-site") == "cross-site":
                return await fail(403, "Cross-site API request blocked")
            if os.getenv("MOUSIKE_RATE_LIMIT", "1") != "0":
                category = (
                    "stream"
                    if path.startswith("/api/stream/")
                    else "ai"
                    if path.startswith("/api/ai/")
                    else "download"
                    if path.startswith("/api/download/")
                    else "api"
                )
                maximum = {"stream": 160, "ai": 24, "download": 12, "api": 140}[
                    category
                ]
                key = ((scope.get("client") or ("local",))[0], category)
                now = time.monotonic()
                tokens, at = self.buckets.get(key, (float(maximum), now))
                tokens = min(maximum, tokens + (now - at) * maximum / 60)
                if tokens < 1:
                    return await fail(
                        429,
                        "Too many requests. Please try again shortly.",
                        {"Retry-After": "5"},
                    )
                self.buckets[key] = (tokens - 1, now)
                self.buckets.move_to_end(key)
                while len(self.buckets) > 2048:
                    self.buckets.popitem(last=False)
            if method in ("POST", "PUT", "PATCH"):
                try:
                    if int(headers.get("content-length", "0")) > 1_000_000:
                        return await fail(413, "Request is too large")
                except ValueError:
                    return await fail(400, "Invalid content length")
                body = bytearray()
                while True:
                    part = await receive()
                    if part["type"] == "http.disconnect":
                        return
                    body.extend(part.get("body", b""))
                    if len(body) > 1_000_000:
                        return await fail(413, "Request is too large")
                    if not part.get("more_body"):
                        break
                delivered = False
                original_receive = receive

                async def buffered_receive():
                    nonlocal delivered
                    if not delivered:
                        delivered = True
                        return {
                            "type": "http.request",
                            "body": bytes(body),
                            "more_body": False,
                        }
                    return await original_receive()

                receive = buffered_receive

        async def safe_send(message):
            if message["type"] == "http.response.start":
                out = list(message.get("headers", []))
                out += [
                    (b"x-content-type-options", b"nosniff"),
                    (b"x-frame-options", b"SAMEORIGIN"),
                    (b"referrer-policy", b"strict-origin-when-cross-origin"),
                    (
                        b"permissions-policy",
                        b"camera=(), microphone=(), geolocation=()",
                    ),
                ]
                if path.startswith("/api/ai/") or path.startswith("/api/discovery/"):
                    out.append((b"cache-control", b"no-store"))
                if path.endswith(".html") or path == "/":
                    out.append((b"cache-control", b"no-cache"))
                    out.append(
                        (
                            b"content-security-policy",
                            b"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data: blob:; media-src 'self' blob:; connect-src 'self'; frame-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'",
                        )
                    )
                message["headers"] = out
            await send(message)

        await self.app(scope, receive, safe_send)
