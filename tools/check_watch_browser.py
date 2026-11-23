#!/usr/bin/env python3
"""Check the optional adapter against a real published Rinku JAR without loading native Chromium."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import urllib.request
from zipfile import ZipFile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--jdk', type=Path, required=True)
args = p.parse_args()


def fetch(url, limit):
    request = urllib.request.Request(url, headers={'User-Agent': 'MinecraftParty-Watch-Contract/0.10 (+https://github.com/SigmaMiaek/minecraft-party)'})
    with urllib.request.urlopen(request, timeout=30) as response:
        content = response.read(limit + 1)
        if len(content) > limit:
            raise RuntimeError('Rinku response exceeds contract-check size limit')
        return content


versions = json.loads(fetch('https://api.modrinth.com/v2/project/rinku/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%2226.2%22%5D', 2 * 1024 * 1024))
version = next(v for v in versions if v['version_type'] == 'release' and v['version_number'].startswith('3.'))
file = next(f for f in version['files'] if f['primary'])
assert file['url'].startswith('https://cdn.modrinth.com/')
raw = fetch(file['url'], 32 * 1024 * 1024)
assert hashlib.sha512(raw).hexdigest() == file['hashes']['sha512']
with ZipFile(io.BytesIO(raw)) as jar:
    manifest = json.loads(jar.read('fabric.mod.json'))
    assert manifest['id'] == 'rinku'
    for name in ('de/keksuccino/rinku/Rinku.class', 'de/keksuccino/rinku/RinkuBrowser.class'):
        assert name in jar.namelist()
output = Path('build/watch-contract')
output.mkdir(parents=True, exist_ok=True)
artifact = output / 'rinku.jar'
artifact.write_bytes(raw)
contracts = {
    'de.keksuccino.rinku.Rinku': ['public static boolean isInitialized();',
        'public static de.keksuccino.rinku.RinkuBrowser createBrowser(java.lang.String, boolean, int, int);'],
    'de.keksuccino.rinku.RinkuBrowser': ['public net.minecraft.resources.Identifier getTextureIdentifier();',
        'public void sendMousePress(int, int, int);', 'public void sendMouseRelease(int, int, int);', 'public void close();']
}
for name, methods in contracts.items():
    actual = subprocess.check_output([str(args.jdk / 'bin/javap'), '-public', '-classpath', str(artifact), name], text=True)
    for method in methods:
        assert method in actual, (name, method)
print('Published Rinku adapter contract verified:', version['version_number'], 'SHA-512 matched Modrinth. Chromium was not started.')
