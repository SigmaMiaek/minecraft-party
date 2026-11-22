#!/usr/bin/env python3
"""Read-only check of the deployed directory; never creates rooms or prints codes."""
import json
import time
import urllib.request

origin = 'https://minecraft-party.wojciechowskaola57.workers.dev'
deadline = time.monotonic() + 300


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(origin + path, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=10) as response:
        assert response.headers['Cache-Control'] == 'no-store'
        return json.load(response)


while True:
    try:
        health = request('/health')
        assert health['service'] == 'minecraft-party-codes' and health['protocol'] == 1
        assert health['partyProtocols'] == [4, 5] and health['maxPlayers'] == 10
        for protocol in (4, 5):
            page = request('/v1/list', {'page': 0, 'size': 1, 'protocol': protocol})
            assert page['publicLobbies'] is True and page['protocol'] == protocol
            assert page['page'] == 0 and page['pages'] >= 1 and len(page['lobbies']) <= 1
            for row in page['lobbies']:
                assert set(row) == {'code', 'name', 'host', 'players', 'limit', 'inWorld', 'minecraft', 'protocol'}
                assert row['protocol'] == protocol and row['minecraft'] == '26.2'
                assert 1 <= row['players'] <= row['limit'] <= (10 if protocol == 5 else 8)
        print('Live directory: MPT4/MPT5 discovery, ten-player support and no-store responses verified. No rooms created.')
        break
    except (OSError, ValueError, KeyError, AssertionError):
        if time.monotonic() >= deadline:
            raise SystemExit('Live directory did not report MPT5 and ten-player support; inspect the Cloudflare deployment.') from None
        time.sleep(10)
