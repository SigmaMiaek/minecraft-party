# Internet przez publiczny relay e4mc — 0.5.2

Użytkownik zaakceptował publiczny relay e4mc po próbie Starlink → hotspot. To zmienia wcześniejsze wymaganie „bez relaya”. Nadal nie trzeba wynajmować hostingu, tworzyć konta w dodatkowym serwisie, uruchamiać serwera Minecraft ani instalować VPN. Mod nie zależy od Essential ani jego infrastruktury.

## Instalacja i próba

1. Oba komputery: Minecraft 26.2, Java 25, Fabric Loader 0.19.3+, Fabric API 0.161.0+26.2 oraz `minecraft-party-0.5.2+26.2.jar`. Usuń poprzedni JAR Party i uruchom ponownie grę. Utwórz nowe Party. Instalowanie moda e4mc nie jest wymagane. Poprawka wznowienia odczytu TLS wymaga aktualizacji hosta, a menu autodołączenia — gościa.
2. Host: Party → **Internet (relay e4mc)** → Utwórz Party. Ten tryb jest domyślny; przycisk przełącza także na bezpośredni Internet IPv4/UPnP i LAN. Migracja wcześniejszej konfiguracji Internet wybiera relay; wcześniejszy jawny LAN pozostaje LAN.
3. Przy pierwszym tworzeniu pobierana jest biblioteka natywna systemu z CDN e4mc. Pobieranie i przygotowanie wykonuje wątek roboczy. Otrzymany plik musi mieć dokładnie przypięty SHA-256; w przeciwnym razie nie zostanie załadowany. Przerwane pobranie można ponowić. Cache jest w `.minecraft-party/cache` instancji gry.
4. Po komunikacie gotowości skopiuj **nowe** zaproszenie `mp3:` i wyślij prywatnie znajomemu. To nadal długi kod, bez usługi sześciocyfrowych kodów. Gość używa Dołącz Party.
5. Host uruchamia świat przez zwykłe Singleplayer albo wybiera serwer Multiplayer. Istniejący most i menedżer sesji wykonują dołączanie do gry. Zamknięcie świata nie zamyka tunelu lobby. Opuszczenie całego Party zamyka QUIC i przydział domeny.

Wydanie jest prototypem integracji. Pełny test z publicznym relayem, dwa komputery, Starlink/hotspot oraz rozgrywka **nie zostały wykonane przez narzędzia w tej sesji**. Przydział domeny nie jest dowodem wejścia gości do Minecrafta.

## Jak działa transport

1. Host otwiera listener TLS Party wyłącznie na `127.0.0.1`.
2. Przez HTTPS pyta `broker.e4mc.link/getBestRelay` o węzeł. Odpowiedź ma limit 8 KiB; akceptujemy wyłącznie poprawny host pod `e4mc.link` i port UDP 1024–65535.
3. Łączy się wychodzącym QUIC z ALPN `quiclime`, systemowym zaufaniem certyfikatów i weryfikacją nazwy relaya. Kontrolny strumień żąda `request_domain_assignment` i czeka na `domain_assignment_complete` z poprawną domeną. Nie pokazujemy zdalnych broadcastów jako komunikatów UI.
4. Gość łączy się TCP z przydzieloną domeną na porcie 25565. Jawny prefiks routingu Minecraft wskazuje tę domenę. Nie zawiera nicku, UUID, zaproszenia ani klucza. Publiczna brama używa go do wyboru tunelu.
5. Brama przekazuje prefiks i dalsze bajty przez strumień QUIC. Host usuwa **dokładnie** poprawny, ograniczony prefiks dla swojej domeny; pozostałe bajty trafiają do listenera TLS. Błędny prefiks zamyka strumień, a nie zostaje przekazany do Minecrafta.
6. Następuje wewnętrzne TLS 1.3 Party z dotychczasowym pinningiem certyfikatu i uwierzytelnieniem sekretem. To nim przechodzą lobby, poświadczenia oraz zaszyfrowany strumień gry. Relay przekazuje te bajty bez kluczy Party.

Most zintegrowanego serwera i ochrona prefiksu prawdziwego loginu Minecrafta są bez zmian. Prefiks do routingu nie jest logowaniem do świata. Każdy gracz nadal przechodzi uwierzytelnienie vanilla swoim kontem.

## Bezpieczeństwo i ograniczenia

- Operator relaya zna adresy połączeń i może obserwować czas, rozmiar lub przerywać ruch. Pinning wewnętrznego TLS chroni treść przed samym relayem. Zaproszenie pozostaje sekretem; przydzielona domena sama nie daje dostępu do lobby.
- Są limity ramek, 32 równoczesne strumienie przychodzące i backpressure przy przekazywaniu do lokalnego TCP. Nie zmieniamy routera ani zapory systemowej.
- CGNAT nie wymaga w tym trybie przychodzącego IPv4 hosta. Potrzebne są jednak dostęp do HTTPS i wychodzący UDP do węzła e4mc, a u gościa wychodzący TCP 25565. Blokada UDP lub awaria usługi nadal może uniemożliwić połączenie. Nie ma fallbacku QUIC przez TCP, TURN ani gwarancji dostępności bezpłatnej usługi.
- Cały tunel e4mc jest zewnętrzną zależnością. To nie jest bezpośrednie P2P. Prywatność i dostępność mają inne granice niż w LAN.
- Krótkie utraty pakietów obsługuje QUIC. Po utracie całej sesji i domeny trzeba utworzyć Party ponownie oraz przesłać nowy kod. Nie obiecujemy automatycznego odzyskania domeny ani migracji hosta.
- Implementacja używa standardowego portu Minecraft e4mc 25565 i domen pod `e4mc.link`. Zmiana publicznego protokołu/domen lub portów przez operatora może wymagać aktualizacji moda.
- Biblioteki są wbudowane jako kod Java Netty QUIC z przypiętego commita, bez całego moda e4mc. Właściwa biblioteka natywna jest pobierana przy pierwszym użyciu. Zobacz [pochodzenie, licencje i zmiany](../vendor/netty-quic/UPSTREAM.md).

## Diagnostyka

Na obu komputerach: Party → Sieć… → Odśwież → Kopiuj raport. Host po udanym tworzeniu powinien mieć `Mode: E4MC_RELAY`; u gościa jest `Guest transport: E4MC_RELAY`. Raport nie zawiera domeny Party, adresów IP, zaproszenia ani ID kont.

Od 0.5.2 raport hosta pokazuje liczbę strumieni, poprawnych/odrzuconych prefiksów, wznowień odczytu po samym prefiksie, lokalnych połączeń TLS i bajtów w obu kierunkach. `World bridge stage: ANNOUNCED` oznacza ogłoszenie zintegrowanego świata; `Host world available` pokazuje obecność celu w stanie lobby. U gościa `Incoming world available`, `Incoming target`, `Auto join stage` i `Client screen` pomagają rozróżnić brak ogłoszenia, oczekiwanie na menu, próbę i błąd logowania. Raport zawiera tylko typ celu i nazwę klasy ekranu, bez adresu serwera.

`relay_native` oznacza problem przygotowania/załadowania biblioteki, `relay_broker` pobrania węzła, `relay_timeout` brak przydziału w czasie, a `relay_unavailable` nieudane zestawienie QUIC. W fazie dołączania zachowano osobne błędy TCP/TLS/lobby. Wewnętrzne błędy nie są traktowane jako powód do przejścia na nieszyfrowane połączenie.

`relay_tls_timeout` / `relay_tls_error` potwierdzają TCP do bramy relaya, bez twierdzenia, że dotarto do hosta. W 0.5.0/0.5.1 przyczyną mógł być odtworzony błąd manualnego odczytu po prefiksie. Zaktualizuj hosta i porównaj oba raporty po kontrolowanej próbie. Lunar może uruchamiać mody Fabric, ale nie potwierdzono kompatybilności tej integracji z Lunar w sesji; niestandardowe menu nie jest już odrzucane tylko z powodu swojej klasy.

## Źródła protokołu

- [e4mc — oficjalny projekt](https://e4mc.link/)
- [Klient QuiclimeSession i dependencies](https://github.com/vgskye/e4mc-minecraft-architectury/blob/rererewrite/common/src/main/java/link/e4mc/QuiclimeSession.java)
- [Publiczna brama QUIC i routing Minecraft](https://github.com/vgskye/e4mc-quiclime/blob/kity/src/main.rs)
- [Przypięte źródło Netty QUIC](https://github.com/vgskye/netty-incubator-codec-quic/tree/adb76feb04d014ad5fd00e5727b78ac5ada9e1fb)
- [Domyślna polityka CGNAT Starlink](https://starlink.com/support/article/1192f3ef-2a17-31d9-261a-a59d215629f4)
