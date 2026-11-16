#!/usr/bin/env python3
"""Publish one immutable-by-convention prerelease after the Windows/Linux build jobs pass."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--artifacts', type=Path, required=True)
parser.add_argument('--repo', default=os.environ.get('GITHUB_REPOSITORY', 'SigmaMiaek/minecraft-party'))
parser.add_argument('--commit', default=os.environ.get('GITHUB_SHA', ''))
parser.add_argument('--dry-run', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['version']
if not re.fullmatch(r'[0-9][A-Za-z0-9.+_-]*', version):
    raise SystemExit('Invalid release version')
if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repo):
    raise SystemExit('Invalid repository')
filename = f'minecraft-party-{version}.jar'
candidates = list(args.artifacts.rglob(filename))
if len(candidates) != 1:
    raise SystemExit(f'Expected exactly one {filename} in the build artifact, found {len(candidates)}')
jar = candidates[0]
with ZipFile(jar) as package:
    if package.testzip() is not None:
        raise SystemExit('Corrupt JAR')
    metadata = json.loads(package.read('fabric.mod.json'))
    if metadata['version'] != version or metadata['depends']['minecraft'] != properties['minecraft_version']:
        raise SystemExit('JAR metadata differs from the source version')
notes_file = root / 'docs/releases' / f'{version}.md'
notes = notes_file.read_text()
checksum = hashlib.sha256(jar.read_bytes()).hexdigest()
tag = f'v{version}'
print(f'Repository: {args.repo}; prerelease: {tag}; asset: {filename}; SHA-256: {checksum}', flush=True)
if args.dry_run:
    print('Dry run: no repository, tag or release was changed.')
    raise SystemExit(0)
if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
    raise SystemExit('A full source commit SHA is required')
# No shell and no interpolated command strings. Never print environment variables or credentials.
existing = subprocess.run(['gh', 'api', f'repos/{args.repo}/releases/tags/{tag}'], text=True, capture_output=True)
if existing.returncode == 0:
    release = json.loads(existing.stdout)
    print(f'Release already exists; leaving its tag/assets unchanged: {release["html_url"]}')
    raise SystemExit(0)
if '404' not in existing.stderr:
    raise SystemExit('Cannot verify whether the release already exists: ' + existing.stderr.strip())
output = root / 'release-assets' / version
output.mkdir(parents=True, exist_ok=True)
sums = output / 'SHA256SUMS'
sums.write_text(f'{checksum}  {filename}\n')
body = output / 'release-notes.md'
body.write_text(notes + f'\n\nAutomatyczny artefakt Gradle po udanych zadaniach build/test Linux i Windows.\nCommit źródeł: `{args.commit}`.\nSHA-256 JAR: `{checksum}`.\n')
subprocess.run(['gh', 'release', 'create', tag, str(jar), str(sums), '--repo', args.repo,
                '--target', args.commit, '--title', f'Minecraft Party {version} (prototype)',
                '--prerelease', '--notes-file', str(body)], check=True)
