# Netty QUIC dependency

Source: https://github.com/vgskye/netty-incubator-codec-quic

Pinned commit: `adb76feb04d014ad5fd00e5727b78ac5ada9e1fb`

The 106 Java files under `src/main/java` were retrieved from `codec-classes-quic/src/main/java` at that commit. They are the e4mc fork of Netty's QUIC library, Apache-2.0. The Minecraft Party application itself remains MIT. This is a source dependency, not an installation of the e4mc mod. No e4mc Minecraft mixins or Essential classes are included.

Local changes:

- `Quiche.loadNativeLibrary` delegates to Minecraft Party's loader. Downloads use HTTPS with a timeout, a 64 MiB body limit, SHA-256 from the pinned upstream source and an instance-local cache. The original URL/path overrides are disabled. The loader permits HTTPS CDN redirects, without an HTTPS-to-HTTP downgrade. Preparation happens before Quic's class initializer, so a failed download can be retried without poisoning that class.
- `NotNullByDefault` omits the JSR-305 meta-annotation, avoiding an unnecessary annotation dependency. JetBrains compile-time annotations remain.

The native library is build 74, served by `natives.e4mc.link`. Its hashes are in `Quiche.java` and the identical `relay-natives.properties`. Native loading has not been exercised in this sandbox. Native components include quiche and BoringSSL; their licenses are in `license/`. Java compilation emits upstream deprecation warnings on JDK 25.

The original Netty LICENSE and NOTICE, and quiche/BoringSSL licenses, are packaged under `META-INF/licenses/netty-quic/` in the mod JAR.
