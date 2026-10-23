"""Tests share an app, but never share a depleted rate-limit budget."""
import pytest
import main
from security import SecurityMiddleware


@pytest.fixture(autouse=True)
def independent_rate_budgets():
    node=main.app.middleware_stack
    while node is not None:
        if isinstance(node,SecurityMiddleware):
            node.buckets.clear()
        node=getattr(node,'app',None)
    yield
