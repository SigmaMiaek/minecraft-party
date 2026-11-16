#!/usr/bin/env python3
"""Compile against existing Gradle cache when the sandbox cannot open Gradle sockets."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--jdk', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-25-openjdk')))
p.add_argument('--skip-network-tests', action='store_true', help='Explicitly exclude socket tests in restricted sandboxes')
args = p.parse_args()
root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['version']
cache = Path.home() / '.gradle/caches'
mc = cache / 'fabric-loom/26.2/minecraft-client.jar'
if not mc.exists():
    raise SystemExit('Minecraft 26.2 is missing from the Gradle cache; run ./gradlew build online first.')
javac, java = (args.jdk / 'bin' / n for n in ('javac', 'java'))
if not javac.exists():
    raise SystemExit('A complete JDK 25 is required: pass --jdk /path/to/jdk-25')
jars = sorted((cache / 'modules-2/files-2.1').rglob('*.jar'))
classpath = os.pathsep.join(map(str, [mc, *jars]))
classes = root / 'build/offline/classes'
test_classes = root / 'build/offline/test-classes'
for directory in (classes, test_classes):
    if directory.exists(): shutil.rmtree(directory)
    directory.mkdir(parents=True)

def compile_sources(sources, destination, cp):
    argfile = root / 'build/offline/javac.args'
    arguments = ['--release', '25', '-encoding', 'UTF-8', '-classpath', cp, '-d', str(destination), *map(str, sources)]
    argfile.write_text('\n'.join(json.dumps(x) for x in arguments), encoding='utf-8')
    subprocess.run([str(javac), '@' + str(argfile)], check=True)

sources = sorted((root / 'src/main/java').rglob('*.java')) + sorted((root / 'party-core/src/main/java').rglob('*.java'))
sources += sorted((root / 'vendor/netty-quic/src/main/java').rglob('*.java'))
compile_sources(sources, classes, classpath)
for source in (root / 'src/main/resources').rglob('*'):
    if source.is_file():
        destination = classes / source.relative_to(root / 'src/main/resources')
        destination.parent.mkdir(parents=True, exist_ok=True)
        if source.name == 'fabric.mod.json':
            destination.write_text(source.read_text().replace('${version}', version), encoding='utf-8')
        else: shutil.copy2(source, destination)
tests = sorted((root / 'party-core/src/test/java').rglob('*.java')) + sorted((root / 'src/test/java').rglob('*.java'))
if tests:
    runner = root / 'build/offline/OfflineTestRunner.java'
    runner.write_text('''
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.launcher.TagFilter;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class OfflineTestRunner {
  public static void main(String[] args) {
    var builder = LauncherDiscoveryRequestBuilder.request().selectors(selectPackage("pl.minecraftparty"));
    if (args.length > 0) builder.filters(TagFilter.excludeTags("network"));
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(builder.build());
    var writer = new java.io.PrintWriter(System.out);
    listener.getSummary().printTo(writer);
    listener.getSummary().printFailuresTo(writer);
    if (listener.getSummary().getTestsFailedCount() > 0 || listener.getSummary().getTestsFoundCount() == 0) System.exit(1);
  }
}
''', encoding='utf-8')
    cp = os.pathsep.join([str(classes), classpath])
    compile_sources([*tests, runner], test_classes, cp)
    command = [str(java), '-cp', os.pathsep.join([str(test_classes), cp]), 'OfflineTestRunner']
    if args.skip_network_tests:
        print('NETWORK TESTS EXCLUDED explicitly: no claim of tested connections.', flush=True)
        command.append('skip-network')
    result = subprocess.run(command)
    if result.returncode:
        raise SystemExit(result.returncode)
destination = root / f'build/libs/minecraft-party-{version}.jar'
destination.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(classes.rglob('*')):
        if file.is_file(): archive.write(file, file.relative_to(classes).as_posix())
    archive.write(root / 'LICENSE', 'LICENSE')
    for file in (root / 'vendor/netty-quic').rglob('*.txt'):
        archive.write(file, 'META-INF/licenses/netty-quic/' + file.relative_to(root / 'vendor/netty-quic').as_posix())
    archive.write(root / 'vendor/netty-quic/UPSTREAM.md', 'META-INF/licenses/netty-quic/UPSTREAM.md')
print(f'Built {destination} (javac fallback, NOT a successful Gradle invocation)')
