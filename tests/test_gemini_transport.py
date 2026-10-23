"""Offline contract checks for the pinned Gemini transport; no real API keys."""
import json
import httpx
import pytest
import recommender

@pytest.mark.asyncio
async def test_flash_lite_exact_model_and_thought_safe_json(monkeypatch):
    requests=[]
    def handle(request):
        requests.append(request)
        return httpx.Response(200,json={"candidates":[{"content":{"parts":[{"thought":True,"text":"internal reasoning is not JSON"},{"text":'{"status":"ready"}'}]}}]})
    original=httpx.AsyncClient
    monkeypatch.setattr(recommender.httpx,'AsyncClient',lambda **kwargs:original(transport=httpx.MockTransport(handle),**kwargs))
    engine=recommender.Recommender()
    result=await engine.gemini('test-key-not-real-012345',{'task':'status'},{'type':'OBJECT'})
    assert result=={'status':'ready'}
    assert str(requests[0].url)=='https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent'
    body=json.loads(requests[0].content)
    assert body['generationConfig']['thinkingConfig']=={'thinkingLevel':'minimal'}
    assert requests[0].headers['x-goog-api-key']=='test-key-not-real-012345'
    await engine.gemini('test-key-not-real-012345',{'task':'status'},{'type':'OBJECT'})
    assert len(requests)==1
    await engine.gemini('test-key-not-real-012345',{'task':'status'},{'type':'OBJECT'},force=True)
    assert len(requests)==2

@pytest.mark.asyncio
async def test_quota_error_is_actionable_without_exposing_key(monkeypatch):
    original=httpx.AsyncClient
    monkeypatch.setattr(recommender.httpx,'AsyncClient',lambda **kwargs:original(transport=httpx.MockTransport(lambda request:httpx.Response(429,json={'error':{'message':'private key must not be echoed'}})),**kwargs))
    with pytest.raises(RuntimeError,match='quota exhausted') as error:
        await recommender.Recommender().gemini('test-key-not-real-012345',{'task':'status'},{'type':'OBJECT'})
    assert 'private key' not in str(error.value)
