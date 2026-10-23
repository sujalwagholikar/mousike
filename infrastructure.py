"""Bounded public caches and extraction workers. No listener data on disk."""

from __future__ import annotations

import asyncio
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
import copy
import threading
import time


class BoundedCache(OrderedDict):
    """Dict-compatible cache for the original API; entries are (epoch, value)."""

    def __init__(self, capacity=512):
        super().__init__()
        self.capacity = capacity

    def __setitem__(self, key, value):
        super().__setitem__(key, value)
        self.move_to_end(key)
        while len(self) > self.capacity:
            self.popitem(last=False)


class PublicSearchCache:
    """Thread safe, per-query single flight; failed queries aren't cached."""

    def __init__(self, capacity=256, ttl=600):
        self.capacity, self.ttl = capacity, ttl
        self.data = OrderedDict()
        self.flights = {}
        self.lock = threading.RLock()

    def call(self, key, fn):
        from concurrent.futures import Future

        with self.lock:
            item = self.data.get(key)
            if item and time.monotonic() - item[0] < self.ttl:
                self.data.move_to_end(key)
                return copy.deepcopy(item[1])
            flight = self.flights.get(key)
            owner = flight is None
            if owner:
                flight = self.flights[key] = Future()
        if not owner:
            return copy.deepcopy(flight.result(timeout=55))
        try:
            value = fn()
            with self.lock:
                self.data[key] = (time.monotonic(), copy.deepcopy(value))
                self.data.move_to_end(key)
                while len(self.data) > self.capacity:
                    self.data.popitem(last=False)
            flight.set_result(value)
            return copy.deepcopy(value)
        except BaseException as exc:
            flight.set_exception(exc)
            # Retrieve it even if no duplicate caller was waiting.
            flight.exception()
            raise
        finally:
            with self.lock:
                self.flights.pop(key, None)


SEARCH_CACHE = PublicSearchCache()
SEARCH_POOL = ThreadPoolExecutor(max_workers=2, thread_name_prefix="mousike-search")
MEDIA_POOL = ThreadPoolExecutor(max_workers=2, thread_name_prefix="mousike-media")


async def run_worker(fn, *args, media=False):
    """Separate playback from search work so feed refresh cannot starve audio."""
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(MEDIA_POOL if media else SEARCH_POOL, fn, *args)


async def singleflight(cache, key, factory):
    """Share in-flight public metadata requests within the current event loop."""
    loop = asyncio.get_running_loop()
    loop_key = (id(loop), key)
    task = cache.get(loop_key)
    if task is None:
        task = cache[loop_key] = asyncio.create_task(factory())

        def done(_):
            cache.pop(loop_key, None)
            if not task.cancelled():
                task.exception()

        task.add_done_callback(done)
    return await asyncio.shield(task)
