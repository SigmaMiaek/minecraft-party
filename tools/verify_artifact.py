#!/usr/bin/env python3
"""Check the packaged metadata, accessors and localization without starting Minecraft."""
import argparse
import json
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--jdk', type=Path, required=True)
args = p.parse_args()
root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)
artifact = root / f"build/libs/minecraft-party-{properties['version']}.jar"
mc = Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar'
with ZipFile(artifact) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    assert metadata['depends']['minecraft'] == '26.2'
    assert '${version}' not in jar.read('fabric.mod.json').decode()
    entrypoint = metadata['entrypoints']['client'][0].replace('.', '/') + '.class'
    assert entrypoint in jar.namelist()
    mixins = json.loads(jar.read(metadata['mixins'][0]))
    for mixin in mixins['client']:
        assert mixins['package'].replace('.', '/') + '/' + mixin + '.class' in jar.namelist()
    assert 'pl/minecraftparty/core/HostNode.class' in jar.namelist()
    assert 'pl/minecraftparty/network/E4mcRelay.class' in jar.namelist()
    assert 'io/netty/incubator/codec/quic/QuicChannel.class' in jar.namelist()
    for license in ['LICENSE.txt', 'NOTICE.txt', 'UPSTREAM.md', 'license/LICENSE.quiche.txt', 'license/LICENSE.boringssl.txt']:
        assert 'META-INF/licenses/netty-quic/' + license in jar.namelist()
    hashes = dict(line.split('=', 1) for line in jar.read('relay-natives.properties').decode().splitlines() if line and not line.startswith('#'))
    native_source = (root / 'vendor/netty-quic/src/main/java/io/netty/incubator/codec/quic/Quiche.java').read_text()
    for filename, numbers in re.findall(r'case "([^"]+)":\s*return new byte\[\]\{([^}]+)\}', native_source):
        expected = bytes(int(value.strip()) & 255 for value in numbers.split(',')).hex()
        assert hashes[filename] == expected, filename
    assert hashes['netty_quiche_windows_x86_64_74.dll']
    assert hashes['libnetty_quiche_linux_x86_64_74.so']
    en = json.loads(jar.read('assets/minecraftparty/lang/en_us.json'))
    pl = json.loads(jar.read('assets/minecraftparty/lang/pl_pl.json'))
    assert en.keys() == pl.keys()
    for key in en:
        assert en[key].count('%s') == pl[key].count('%s'), key
    used = set()
    for source in (root / 'src/main/java').rglob('*.java'):
        used.update('party.' + key for key in re.findall(r'tr\("([^"\n]+)"[,)]+', source.read_text()))
        used.update('party.message.' + key for key in re.findall(r'(?:message\(|message = |failed\(operation, )"([a-z_]+)"', source.read_text()))
    assert used <= en.keys(), used - en.keys()
    for classfile in [n for n in jar.namelist() if n.endswith('.class')]:
        data = jar.read(classfile)
        assert int.from_bytes(data[6:8], 'big') == 69, classfile  # Java 25
    assert all('essential' not in n.lower() for n in jar.namelist())
for target, fields in {
    'net.minecraft.client.server.IntegratedServer': ['private int publishedPort;', 'private net.minecraft.server.MinecraftServer$MultiplayerScope multiplayerScope;'],
    'net.minecraft.server.network.ServerConnectionListener': ['private final java.util.List<io.netty.channel.ChannelFuture> channels;']
}.items():
    actual = subprocess.check_output([str(args.jdk / 'bin/javap'), '-p', '-classpath', str(mc), target], text=True)
    for field in fields: assert field in actual, (target, field)
print(f'Artifact OK: {artifact.name}; {len(en)} translations in each language; accessor targets match Minecraft 26.2.')
permission_methods = {
    'net.minecraft.server.players.PlayerList': 'public boolean isOp(net.minecraft.server.players.NameAndId);',
    'net.minecraft.server.MinecraftServer': 'public net.minecraft.server.permissions.LevelBasedPermissionSet getProfilePermissions(net.minecraft.server.players.NameAndId);',
}
for target, method in permission_methods.items():
    actual = subprocess.check_output([str(args.jdk / 'bin/javap'), '-p', '-classpath', str(mc), target], text=True)
    assert method in actual, (target, method)
teleport = subprocess.check_output([str(args.jdk / 'bin/javap'), '-c', '-p', '-classpath', str(mc),
                                    'net.minecraft.server.commands.TeleportCommand'], text=True)
assert teleport.count('LiteralArgumentBuilder.requires:(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;') == 2
print('Permission hook targets exist; vanilla teleport has the two expected command-root predicates.')
print('Vendored QUIC classes, native SHA-256 pins and third-party licenses are present; no native library was loaded by this check.')
print('Static artifact checks only; no claim of runtime Mixin, UI or multiplayer verification.')
