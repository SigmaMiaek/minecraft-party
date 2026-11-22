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
        print('Real Worker + SQLite Durable Object: health, approval, capability isolation and revocation passed.')
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
