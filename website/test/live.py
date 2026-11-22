#!/usr/bin/env python3
"""Read-only checks of the public website and the separate Party code service."""
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request
from urllib.parse import urljoin

site = os.environ.get('PARTY_WEBSITE_URL', 'https://sigmamiaek.github.io/minecraft-party/').rstrip('/') + '/'
directory = 'https://minecraft-party.wojciechowskaola57.workers.dev/'
# The site may still link the last released JAR while the next mod build is in CI.
source = (Path(__file__).resolve().parents[1] / 'dist/index.html').read_text()
asset = re.search(r'https://github.com/SigmaMiaek/minecraft-party/releases/download/[^"<>]+\.jar', source).group().encode()

def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'MinecraftParty-Website-Check/1'})
    with urllib.request.urlopen(request, timeout=12) as response:
        return response.read(), response.headers

deadline = time.monotonic() + 180
last_error = ''
while True:
    try:
        body, headers = fetch(site)
        assert b'Tw\xc3\xb3j \xc5\x9bwiat.' in body and b'assets/world.svg' in body, 'Public website has not updated yet'
        assert 'text/html' in headers['Content-Type']
        assert b'https://sigmamiaek.github.io/minecraft-party/' in body, 'Canonical site URL has not updated yet'
        assert asset in body, 'Public download target has not updated to the current mod release yet'
        script, headers = fetch(urljoin(site, 'app.js'))
        assert b'demoStep' in script
        image, _ = fetch(urljoin(site, 'assets/world.svg'))
        assert b'<svg' in image
        health_body, health_headers = fetch(urljoin(directory, 'health'))
        health = json.loads(health_body)
        assert health['service'] == 'minecraft-party-codes' and health['protocol'] == 1
        assert health['autoJoin'] is True
        assert health.get('publicLobbies') is True, 'Public directory service has not updated yet'
        assert health_headers['Cache-Control'] == 'no-store'
        request = urllib.request.Request(urljoin(directory, 'v1/list'), data=b'{"page":0,"size":1}',
                                         headers={'Content-Type': 'application/json', 'User-Agent': 'MinecraftParty-Website-Check/1'})
        with urllib.request.urlopen(request, timeout=12) as response:
            public = json.load(response)
        assert public['publicLobbies'] is True and len(public['lobbies']) <= 1
        for lobby in public['lobbies']:
            assert set(lobby) == {'code', 'name', 'host', 'players', 'limit', 'inWorld', 'minecraft', 'protocol'}
        print('Public website HTML, JavaScript, artwork and unchanged Party directory health verified:', site)
        break
    except (OSError, AssertionError, KeyError, ValueError) as error:
        last_error = f'{type(error).__name__}: {error}'
        if time.monotonic() >= deadline:
            raise RuntimeError('Website publication could not be confirmed: ' + last_error)
        print('Waiting for website deployment:', last_error, flush=True)
        time.sleep(10)
