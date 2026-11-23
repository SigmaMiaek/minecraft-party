#!/usr/bin/env python3
"""Read-only check of the deployed directory; never creates rooms or prints codes."""
import json
import time
import urllib.request

origin = 'https://minecraft-party.wojciechowskaola57.workers.dev'
deadline = time.monotonic() + 300


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(origin + path, data=data, headers={'Content-Type': 'application/json',
        'User-Agent': 'MinecraftParty-Code-Check/0.10', 'Cache-Control': 'no-cache'})
    with urllib.request.urlopen(req, timeout=10) as response:
        assert response.headers['Cache-Control'] == 'no-store', 'Missing no-store response header'
        return json.load(response)


observed = None
stage = 'health'
while True:
    try:
        stage = 'health'
        health = request('/health')
        capabilities = (health.get('partyProtocols'), health.get('maxPlayers'))
        if capabilities != observed:
            print('Deployed capabilities: partyProtocols=%r, maxPlayers=%r' % capabilities, flush=True)
            observed = capabilities
        assert health['service'] == 'minecraft-party-codes' and health['protocol'] == 1, 'Unexpected code service'
        assert health.get('partyProtocols') == [4, 5, 6] and health.get('maxPlayers') == 10, 'MPT6 deployment not visible'
        for protocol in (4, 5, 6):
            stage = 'list MPT' + str(protocol)
            page = request('/v1/list', {'page': 0, 'size': 1, 'protocol': protocol})
            assert page['publicLobbies'] is True and page['protocol'] == protocol, 'Directory protocol mismatch'
            assert page['page'] == 0 and page['pages'] >= 1 and len(page['lobbies']) <= 1, 'Invalid pagination'
            for row in page['lobbies']:
                assert set(row) == {'code', 'name', 'host', 'players', 'limit', 'inWorld', 'minecraft', 'protocol'}
                assert row['protocol'] == protocol and row['minecraft'] == '26.2'
                assert 1 <= row['players'] <= row['limit'] <= (10 if protocol >= 5 else 8)
        print('Live directory: MPT4/MPT5/MPT6 discovery, ten-player support and no-store responses verified. No rooms created.')
        break
    except (OSError, ValueError, KeyError, AssertionError) as error:
        # Only health capabilities, test-owned assertion text and HTTP status are logged.
        # Public room bodies/codes, request bodies and exception URLs are never printed.
        reason = str(error) if isinstance(error, AssertionError) else type(error).__name__
        if hasattr(error, 'code'):
            reason += ' HTTP ' + str(error.code)
        print('Waiting at %s: %s' % (stage, reason), flush=True)
        if time.monotonic() >= deadline:
            raise SystemExit('Live directory did not report MPT6 and ten-player support; inspect the Cloudflare deployment.') from None
        time.sleep(10)
