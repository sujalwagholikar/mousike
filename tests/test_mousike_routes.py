"""Run with: python -m pytest tests/test_mousike_routes.py"""

from fastapi.testclient import TestClient
import main


def test_ui_routes():
    client = TestClient(main.app)
    for path in (
        "/",
        "/onboarding.html",
        "/settings.html",
        "/assets/brand/mousike-static.svg",
        "/assets/brand/mousike-animated.svg",
        "/assets/css/settings.css",
        "/assets/js/mousike.js",
    ):
        assert client.get(path).status_code == 200, path
    assert "Mousike" in client.get("/settings.html").text
    assert client.get("/api/health").json()["service"] == "Mousike"


def test_stateless_profile_and_recommendations():
    client = TestClient(main.app)
    result = client.post(
        "/api/profile", json={"client_id": "listener_001", "name": "Listener"}
    )
    assert result.status_code == 200 and result.json()["storage"] == "browser-only"
    plan = client.post(
        "/api/recommendations",
        json={"profile": {"artists": ["Arijit Singh"], "languages": ["Hindi"]}},
    )
    assert plan.status_code == 200 and len(plan.json()["sections"]) > 0
