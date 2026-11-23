#!/usr/bin/env python3
"""Exercise the real Worker and SQLite Durable Object in Wrangler's local runtime (CI only)."""
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.request
import urllib.error

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryFile(mode='w+') as log:
    process = subprocess.Popen(['npx', '--yes', 'wrangler@4.166.0', 'dev', '--local', '--ip', '127.0.0.1', '--port', '8787'],
                               cwd=root, stdout=log, stderr=log)
    def call(action, body=None, expected=200):
        url = 'http://127.0.0.1:8787/' + ('health' if body is None else 'v1/' + action)
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                status, result = response.status, json.load(response)
                assert response.headers['Cache-Control'] == 'no-store'
        except urllib.error.HTTPError as error:
            status, result = error.code, json.load(error)
        assert status == expected, f'Unexpected status on {action}: {status}'
        return result
    try:
        deadline = time.monotonic() + 90
        while True:
            try:
                assert call('health')['service'] == 'minecraft-party-codes'
                break
            except (OSError, AssertionError):
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError('Wrangler runtime failed to start')
                time.sleep(1)
        # Static assets share the Worker origin with the code directory. These
        # requests must never swallow /health or POST /v1/* below.
        for asset, content_type, marker in [
            ('/', 'text/html', b'Minecraft Party'),
            ('/app.js', 'javascript', b'demoStep'),
            ('/styles.css', 'text/css', b'Figtree'),
            ('/assets/world.svg', 'image/svg+xml', b'<svg'),
        ]:
            with urllib.request.urlopen('http://127.0.0.1:8787' + asset, timeout=5) as response:
                assert response.status == 200
                assert content_type in response.headers['Content-Type']
                assert marker in response.read()
                assert response.headers['X-Content-Type-Options'] == 'nosniff'
        assert call('health')['protocol'] == 1
        invite = 'mp3:' + 'A' * 100  # opaque test payload; this does not simulate a Minecraft session
        registered = call('create', {'invite': invite, 'expiresAt': int(time.time() * 1000) + 300000})
        owner = {'code': registered['code'], 'owner': registered['owner']}
        guest = {'code': registered['code'], 'name': 'PartyTest', 'requestToken': secrets.token_urlsafe(32)}
        requested = call('request', guest)
        assert requested['status'] == 'pending' and 'invite' not in requested
        assert 'invite' not in call('poll', guest)
        call('host', {**owner, 'owner': secrets.token_urlsafe(32)}, 403)
        pending = call('host', owner)['pending']
        assert len(pending) == 1 and pending[0]['check'] == requested['check']
        call('decide', {**owner, 'id': pending[0]['id'], 'allow': True})
        assert call('poll', guest)['invite'] == invite
        call('poll', {**guest, 'requestToken': secrets.token_urlsafe(32)}, 404)
        call('remove', owner)
        call('poll', guest, 404)
        auto = call('create', {'invite': invite, 'expiresAt': int(time.time() * 1000) + 300000, 'autoJoin': True})
        assert auto['autoJoin'] is True
        immediate = call('request', {'code': auto['code'], 'name': 'DirectJoin', 'requestToken': secrets.token_urlsafe(32)})
        assert immediate['status'] == 'approved' and immediate['invite'] == invite
        call('remove', {'code': auto['code'], 'owner': auto['owner']})
        assert call('health')['publicLobbies'] is True
        assert call('health')['partyProtocols'] == [4, 5, 6, 7]
        assert call('health')['maxPlayers'] == 10
        assert call('list', {})['lobbies'] == []
        assert call('list', {'protocol': 5})['lobbies'] == []
        metadata = {'name': 'Public Party', 'host': 'Host', 'players': 1, 'limit': 6,
                    'inWorld': False, 'minecraft': '26.2', 'protocol': 4}
        public = call('create', {'invite': invite, 'expiresAt': int(time.time() * 1000) + 300000,
                                'autoJoin': True, 'publicLobby': True, 'listing': metadata})
        rows = call('list', {})['lobbies']
        assert len(rows) == 1 and rows[0]['code'] == public['code']
        assert set(rows[0]) == set(metadata) | {'code'}
        owner = {'code': public['code'], 'owner': public['owner']}
        call('host', {**owner, 'owner': secrets.token_urlsafe(32), 'listing': metadata}, 403)
        call('host', {**owner, 'listing': {**metadata, 'players': 3, 'inWorld': True}})
        assert call('list', {})['lobbies'][0]['players'] == 3
        modern = call('create', {'invite': invite, 'expiresAt': int(time.time() * 1000) + 300000,
                                 'autoJoin': True, 'publicLobby': True,
                                 'listing': {**metadata, 'protocol': 5, 'limit': 10, 'players': 10}})
        current = call('list', {'protocol': 5})
        assert current['protocol'] == 5 and len(current['lobbies']) == 1
        assert current['lobbies'][0]['code'] == modern['code'] and current['lobbies'][0]['limit'] == 10
        assert len(call('list', {})['lobbies']) == 1
        assert call('list', {})['lobbies'][0]['code'] == public['code']
        call('list', {'protocol': '5'}, 400)
        call('remove', {'code': modern['code'], 'owner': modern['owner']})
        assert call('list', {'protocol': 5})['lobbies'] == []
        handoff = call('create', {'invite': invite, 'expiresAt': int(time.time() * 1000) + 300000,
                                  'autoJoin': True, 'publicLobby': True,
                                  'listing': {**metadata, 'protocol': 7, 'limit': 10}})
        assert call('list', {'protocol': 7})['lobbies'][0]['code'] == handoff['code']
        assert call('list', {'protocol': 6})['lobbies'] == []
        call('remove', {'code': handoff['code'], 'owner': handoff['owner']})
        call('remove', owner)
        assert call('list', {})['lobbies'] == []
        print('Real Worker + SQLite: assets, health, approval, auto-join, ten-player rooms, protocol isolation and revocation passed.')
    except Exception:
        # Avoid logging request bodies/capabilities, including from Wrangler request logs.
        raise
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
