#!/usr/bin/env python3
"""Read-only checks of the public site after Cloudflare's Git auto-deployment."""
import json
import time
import urllib.error
import urllib.request

origin = 'https://minecraft-party.wojciechowskaola57.workers.dev'

def fetch(path):
    request = urllib.request.Request(origin + path, headers={'User-Agent': 'MinecraftParty-Website-Check/1'})
    with urllib.request.urlopen(request, timeout=12) as response:
        return response.read(), response.headers

deadline = time.monotonic() + 180
last_error = ''
while True:
    try:
        body, headers = fetch('/')
        assert b'Tw\xc3\xb3j \xc5\x9bwiat.' in body and b'assets/world.svg' in body, 'Public website has not updated yet'
        assert 'text/html' in headers['Content-Type']
        assert headers['X-Content-Type-Options'] == 'nosniff'
        script, headers = fetch('/app.js')
        assert b'demoStep' in script
        health_body, health_headers = fetch('/health')
        health = json.loads(health_body)
        assert health['service'] == 'minecraft-party-codes' and health['protocol'] == 1
        assert health['autoJoin'] is True
        assert health_headers['Cache-Control'] == 'no-store'
        print('Public website HTML, JavaScript, security headers and unchanged directory health verified:', origin)
        break
    except (OSError, AssertionError, KeyError, ValueError) as error:
        last_error = f'{type(error).__name__}: {error}'
        if time.monotonic() >= deadline:
            raise RuntimeError('Cloudflare publication could not be confirmed: ' + last_error)
        print('Waiting for Cloudflare Git deployment:', last_error, flush=True)
        time.sleep(10)
