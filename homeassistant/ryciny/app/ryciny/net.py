"""Small HTTP client on the standard library."""

from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request

try:
    import certifi

    _SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:  # the system's certificates
    _SSL = ssl.create_default_context()

AGENT = "Ryciny-HA/0.10 (Home Assistant add-on; bird, dog and aircraft frame)"


class HttpError(IOError):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(f"HTTP {code}: {message}")
        self.code = code


def get(url: str, headers: dict | None = None, timeout: float = 20) -> bytes:
    return _request(urllib.request.Request(url, headers={"User-Agent": AGENT, **(headers or {})}), timeout)


def get_json(url: str, timeout: float = 20):
    return json.loads(get(url, timeout=timeout))


def post_json(url: str, body: dict, headers: dict | None = None, timeout: float = 180):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST",
                                 headers={"User-Agent": AGENT, "Content-Type": "application/json", **(headers or {})})
    return json.loads(_request(req, timeout))


def _request(req: urllib.request.Request, timeout: float) -> bytes:
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_SSL) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        raise HttpError(e.code, e.read()[:400].decode(errors="replace")) from e
