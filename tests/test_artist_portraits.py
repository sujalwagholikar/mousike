"""Offline deterministic tests for Aurora's public-artist portrait discovery."""

import pytest
from fastapi.testclient import TestClient
import main


def test_public_image_url_allowlist():
    assert main.safe_public_photo("https://upload.wikimedia.org/a.jpg")
    assert main.safe_public_photo("https://commons.wikimedia.org/example.png")
    assert not main.safe_public_photo("http://upload.wikimedia.org/image.png")
    assert not main.safe_public_photo("https://upload.wikimedia.org.evil.example/x")
    assert not main.safe_public_photo("https://example.org/photo.png")


@pytest.mark.asyncio
async def test_wikipedia_batch_artist_redirects(monkeypatch):
    class Response:
        def raise_for_status(self):
            pass

        def json(self):
            return {
                "query": {
                    "redirects": [
                        {"from": "Pritam Chakraborty", "to": "Pritam (composer)"}
                    ],
                    "pages": [
                        {
                            "title": "Arijit Singh",
                            "thumbnail": {
                                "source": "https://upload.wikimedia.org/arijit.jpg"
                            },
                        },
                        {
                            "title": "Pritam (composer)",
                            "thumbnail": {
                                "source": "https://upload.wikimedia.org/pritam.jpg"
                            },
                        },
                        {
                            "title": "Drake (musician)",
                            "thumbnail": {"source": "https://example.com/fake.jpg"},
                        },
                    ],
                }
            }

    class Client:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            pass

        async def get(self, url, params):
            assert params["pithumbsize"] == 260
            assert "Pritam Chakraborty" in params["titles"]
            return Response()

    monkeypatch.setattr(main.httpx, "AsyncClient", Client)
    result = await main.wikipedia_portraits(["Arijit Singh", "Pritam", "Drake"])
    assert result["Arijit Singh"]["image"].endswith("arijit.jpg")
    assert result["Pritam"]["image"].endswith("pritam.jpg")
    assert "Drake" not in result


def test_portrait_api_batch_no_profile_persistence(monkeypatch):
    async def fake(names):
        return {
            name: {
                "image": "https://upload.wikimedia.org/test.jpg",
                "source": "https://en.wikipedia.org/wiki/" + name.replace(" ", "_"),
                "provider": "Wikipedia / Wikimedia Commons",
            }
            for name in names
        }

    monkeypatch.setattr(main, "wikipedia_portraits", fake)
    client = TestClient(main.app)
    data = client.post(
        "/api/artist-portraits",
        json={"names": ["Arijit Singh", "Shreya Ghoshal", "Arijit Singh"]},
    )
    assert data.status_code == 200
    assert len(data.json()["portraits"]) == 2
    assert data.json()["portraits"]["Arijit Singh"]["image"].startswith("https://")
    assert not hasattr(main, "portrait_database")
