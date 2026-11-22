# Minecraft Party — prototyp 0.7.0

**Pobierz:** [wydania Minecraft Party](https://github.com/SigmaMiaek/minecraft-party/releases). Wydanie 0.7.0 jest publikowane po udanej kompilacji i testach Windows/Linux oraz katalogu kodów. [Starsze lokalne prototypy](downloads/README.md) pozostają dostępne.

**Nowe w 0.7.0:** gwiazdka obok gracza przekazuje mu lidera w lobby. Opuść Party przygotowuje kolejnego połączonego uczestnika i przenosi do niego pozostałych, zanim stary lider zamknie połączenie. [Działanie i ograniczenia migracji](docs/LEADERSHIP.md). Kod jest domyślnie ukryty, z oczkiem i ikoną kopiowania obok; poprawny kod dołącza bez akceptacji. Adres Cloudflare nadal ustawia się automatycznie. Każdy posiadacz kodu może wejść do Party. Wszyscy muszą zaktualizować mod i trzeba wdrożyć aktualny Worker.

Niezależny mod kliencki Fabric z lobby istniejącym poza światem i szyfrowanym tunelem do zintegrowanego serwera. Projekt nie zawiera kodu, API ani zależności Essential Mod.

**Stan: skompilowany prototyp z tunelem przez publiczny relay e4mc i katalogiem sześciocyfrowych kodów. Połączenie z publicznym relayem, rozgrywka na dwóch klientach oraz Starlink/hotspot nie zostały w tym środowisku potwierdzone.**

## Instalacja prototypu

1. Obaj gracze instalują Minecraft Java **26.2**, Fabric Loader **0.19.3 lub nowszy zgodny z 26.2**, Fabric API **0.161.0+26.2** i Java **25**.
2. Skopiuj `build/libs/minecraft-party-0.7.0+26.2.jar` do katalogu `mods` każdej instancji. Usuń poprzedni JAR Party i uruchom ponownie Minecrafta. Instalowanie moda e4mc nie jest wymagane.
3. Użyj dwóch różnych, prawidłowo zalogowanych kont Minecraft. Ten prototyp zachowuje logowanie vanilla; uruchomienie developerskie bez sesji Microsoft nie sprawdza prawdziwego dołączania do świata.
4. Host tworzy Party. Mod automatycznie korzysta z e4mc i domyślnej usługi kodów. Przy pierwszej próbie pobiera zweryfikowaną bibliotekę natywną; potrzebne są HTTPS i wychodzące UDP.
5. Znajomy wpisuje sześć cyfr i od razu rozpoczyna połączenie. Szczegóły: [Kody Party](docs/CLOUDFLARE.md).

Docelowy przepływ zaimplementowany w kodzie:

**Party → Utwórz Party → Kopiuj kod → znajomy wkleja kod i dołącza → Wybierz świat → automatyczne dołączenie gości.**

Po zamknięciu świata lobby ma zachować uczestników, a kolejny świat otrzymuje nowy identyfikator sesji. Nowy uczestnik może dołączyć do już udostępnionego świata. Menu ESC zawiera przycisk Party. W razie błędu logowania można ręcznie ponowić dołączenie przyciskiem w Party; mod nie powtarza bez końca błędnego logowania. Cały powyższy przepływ wymaga jeszcze testów w działającej grze.

Limit 2–8 obejmuje hosta i odpowiada limitowi zintegrowanego serwera 26.2. Nazwa i domyślny limit zapisują się w `config/minecraft-party.json`. Język ekranu wynika z języka Minecrafta: polski lub angielski.

## UI i wspólny serwer multiplayer

Ekran Party korzysta z przyciemnionej tekstury dirta dostarczanej przez Minecrafta oraz zwykłych przycisków vanilla. W szerokim oknie ma listę graczy z głowami po lewej, czat lobby pośrodku i ustawienia hosta po prawej. W mniejszym oknie są zakładki Gracze / Czat lobby / Ustawienia. W menu głównym aktywne Party ma boczny panel listy i czatu, jeżeli obok standardowych przycisków jest wystarczająco dużo miejsca.

Czat rzeczywiście przesyła wiadomości kanałem TLS lobby. Ma limit 256 znaków, historię 64 wiadomości i ograniczenie tempa. Enter lub przycisk Wyślij wysyła wiadomość; strzałki przewijają historię. Głowy są renderowane przez Minecrafta z jego asynchroniczną pamięcią profili, z domyślnym skinem do czasu pobrania.

**Host: Party → Ustawienia hosta → Wspólny serwer: WŁ. → Wstecz → zwykłe menu Minecrafta → Multiplayer → wybierz serwer.** Przyciski Singleplayer i Multiplayer zostały usunięte z ekranu Party; wybór gry odbywa się standardowo. Dopiero po pomyślnym wejściu hosta do gry Party przekazuje uczestnikom oryginalny adres serwera i wywołuje u nich standardowy ConnectScreen. Adres domeny, port i rekordy SRV obsługuje Minecraft każdego uczestnika. Goście mogą także ponowić połączenie przyciskiem Dołącz do świata w Party.

Nie jest to tunel zewnętrznego serwera przez hosta: każdy gracz łączy się bezpośrednio z tym samym adresem, używa własnego konta i podlega whiteliście, limitowi miejsc oraz wymaganiom modów tego serwera. Po wyjściu hosta lub wyłączeniu wspólnego serwera sesja jest wycofywana i uczestnicy wracają do lobby. Ustawienie zapisuje się w konfiguracji jako `shareMultiplayer`. Adresy `localhost` / loopback są odrzucane, ponieważ wskazują inny komputer u każdego gracza.

Zalecana wersja dla wszystkich uczestników: 0.5.2. Zaproszenia przez e4mc używają **mp3:**; bezpośrednie nadal **mp2:**. Stare mp1 nie są obsługiwane. Poprawka TLS działa na hoście, a poprawka menu na gościu, dlatego aktualizuj oba komputery, uruchom ponownie grę i utwórz nowe Party.

## Poprawka 0.5.2 — odczyt TLS i menu innych klientów

Odtworzono błąd z 0.5.0/0.5.1: przy wyłączonym AUTO_READ, jeśli prefiks routingu przychodził bez pierwszych bajtów TLS, usunięcie dekodera zatrzymywało dalszy odczyt. Gość mógł widzieć timeout TLS, także przy drugim połączeniu do świata. Teraz po samym prefiksie tunel jawnie wznawia odczyt. Nowy test nie przeszedł przed poprawką i przechodzi po niej.

Automatyczne dołączanie nie wymaga już konkretnej klasy TitleScreen/PartyScreen. Zwykłe menu i niestandardowe menu klienta są dopuszczane, a aktywny świat, połączenie, ładowanie i wybór/tworzenie innego świata pozostają chronione. To poprawka ogólnego warunku menu, bez zależności od API Lunar. Sam Lunar nie został tu uruchomiony; jego rzeczywista zgodność wymaga testu. [Oficjalna instrukcja modów Fabric w Lunar](https://www.lunarclient.com/news/how-to-add-your-own-mods-to-lunar-client).

Diagnostyka ma dodatkowo liczniki strumieni relaya, prefiksów, połączeń z lokalnym TLS i bajtów oraz etap udostępniania świata i decyzję autodołączenia. Nie zawiera adresów, domeny Party ani identyfikatorów świata. Timeout przez relay nie twierdzi już, że TCP dotarło do hosta: potwierdza jedynie połączenie z bramą e4mc. Zgłoszono później udane połączenie Party, ale nie otrzymano raportów potwierdzających przyczynę problemu z wejściem do świata. Narzędzia tej sesji nie wykonały rzeczywistej rozgrywki ani testu Lunar.

## Poprawka 0.5.1 — odblokowanie Dołącz dla mp3

W 0.5.0 przycisk Dołącz sprawdzał wyłącznie `mp2:`, mimo że parser obsługiwał już relayowe `mp3:`. Teraz UI i parser korzystają ze wspólnego rozpoznawania obu prefiksów, ignorują białe znaki na brzegach wklejonego kodu, a przycisk odświeża się od razu po zmianie pola. Podpowiedź PL/EN wymienia oba formaty. Pełna walidacja i wygaśnięcie nadal są sprawdzane podczas dołączania.

Do naprawy zablokowanego przycisku wystarczy aktualizacja klienta dołączającego i restart jego gry. Host 0.5.0 może zachować dotychczasowe Party i ważne zaproszenie mp3. Nowy test sprawdza, że kody wygenerowane przez oba transporty przechodzą tę samą kontrolę, z której korzysta UI.

## Nowości 0.5.0 — publiczny relay e4mc

Po zgłoszeniu problemu Starlink → hotspot użytkownik zaakceptował użycie publicznego relaya e4mc. Host zestawia wychodzący QUIC, gość łączy się z bramą TCP, a nasz kanał TLS 1.3 przechodzi wewnątrz tunelu. Zachowano pinning, sekret zaproszenia, indywidualne wznowienia oraz standardowe uwierzytelnienie Minecrafta. Lobby działa w kodzie przed uruchomieniem świata; jego tunel nie jest zamykany wraz ze światem.

Nie ma dodatkowego konta, własnego hostingu ani zależności od Essential. Kod Java Netty QUIC jest wbudowany; przy pierwszym tworzeniu Party mod pobiera bibliotekę natywną z przypiętym SHA-256. Nie jest wymagany cały mod e4mc ani osobny program. Przycisk trybu pozwala jawnie wybrać relay, bezpośredni Internet lub LAN. Nie przełączamy transportu po cichu.

Operator relaya zna adresy i metadane połączeń, ale nie ma kluczy wewnętrznego TLS Party. Dostępność zależy od usługi e4mc oraz łączności UDP/TCP; nie zapewniamy gwarancji. Po zerwaniu całego tunelu i utracie przydzielonej domeny trzeba utworzyć Party ponownie. Integracja jest prototypem, nie deklaracją udanego testu przez internet. [Transport, instalacja i ograniczenia](docs/E4MC_RELAY.md).

## Nowości 0.3.0 — lista pod ESC i uprawnienia świata

Menu ESC pokazuje po prawej samą listę graczy z głowami i statusami, bez czatu. W razie potrzeby standardowe przyciski i nagłówek menu przesuwają się w lewo, aby obie kolumny zmieściły się bez nachodzenia. W bardzo małym oknie pozostaje przycisk Party. Czat nadal jest w ekranie Party i bocznym panelu menu głównego.

W aktualnym świecie singleplayer hosta obok nicku uczestnika Party pojawia się **OP+ / OP−**. OP+ nadaje operatora na poziomie 2 (GAMEMASTER, standardowy poziom operatora serwera zintegrowanego); OP− cofa to nadanie. Gracz musi najpierw zalogować się do świata, żeby otrzymać operatora. Gospodarz nie traci swoich zwykłych uprawnień, a wcześniej istniejące nadania vanilla nie są usuwane.

W ustawieniach hosta jest przełącznik **„/tp dla Party”**, domyślnie wyłączony. Pozwala uczestnikom aktualnego świata używać vanilla `/tp` i `/teleport` z nickami lub współrzędnymi bez pozostałych komend operatora, np. `/tp Nick` lub `/tp 100 70 200`. Selektory `@s`, `@a` itp. zachowują osobne wymaganie vanilla i wymagają operatora. Wyłączenie przełącznika nie odbiera jawnie nadanego OP.

Nadania OP są w pamięci: kończą się wraz z udostępnianiem świata lub usunięciem uczestnika i nie trafiają do `ops.json` ani kolejnego świata. Preferencja `allowPartyTeleport` zapisuje się w konfiguracji hosta i jest stosowana po uruchomieniu następnego świata. Te kontrolki są niedostępne na zewnętrznym serwerze multiplayer — tam obowiązują uprawnienia jego administratora. Przełączanie uprawnień odświeża drzewo komend na klientach.

## Nowości 0.4.0 — P2P bez usługi kodów

W wydaniu 0.4.0 **pozostały długie zaproszenia mp2**, bez sześciocyfrowych kodów, serwisu lobby i relaya. W 0.5.0 użytkownik zaakceptował publiczny relay e4mc. Nie wdrożono żadnej usługi w Railway ani Sites.

W 0.4.0 domyślny był **Tryb: Internet (P2P)**, z osobnym **Trybem: LAN**. W 0.5.0 oba są opcjonalne obok domyślnego relaya. Bezpośredni Internet korzysta z publicznego IPv4 na interfejsie komputera; jeśli go nie ma, mod wyszukuje lokalną bramę UPnP IGD v1/v2 i prosi o tymczasowe mapowanie portu TCP warstwy TLS. Do zaproszenia wpisywany jest zgłoszony przez router publiczny adres i faktycznie wybrany port zewnętrzny. Prywatny adres lub adres CGNAT nie jest publikowany jako internetowy. Gość nadal tylko wkleja całe zaproszenie; nie wpisuje IP.

Mapowanie ma żądany czas 1800 sekund, jest odnawiane co 900 sekund i usuwane przy opuszczeniu Party, jeśli nadal należy do tej samej sesji. Mod nie nadpisuje istniejących mapowań innych aplikacji i nie przyjmuje mapowania bezterminowego. Operacje routera mają timeouty, ograniczone odpowiedzi, zabezpieczony parser XML i możliwość anulowania tworzenia. Po utracie mapowania albo zmianie publicznego IP trzeba utworzyć Party ponownie.

Jeśli publiczny adres lub bezpieczne czasowe UPnP są niedostępne, tworzenie Party w trybie Internet kończy się błędem. Nie ma automatycznego, ukrytego przełączenia na LAN. Tryb LAN wybierasz samodzielnie. CGNAT, podwójny NAT, wyłączone UPnP, routery bez obsługi wymaganych akcji i zapory mogą uniemożliwić połączenie. IPv6 i ICE nadal nie są zaimplementowane.

Potwierdzenie mapowania i utworzenie zaproszenia **nie są testem dostępności z internetu**. Tego wydania nie sprawdzono na dwóch komputerach ani prawdziwym routerze w tej sesji.

## Poprawka 0.4.1 — „Host unreachable” i diagnostyka

W 0.4.0 interfejs VPN mógł dostarczyć adres wyglądający na publiczny, przez co mod pomijał UPnP i umieszczał w zaproszeniu adres niedostępny poza tą siecią VPN. Dotyczy to m.in. Hamachi. W 0.4.1 wybór kandydatów i interfejsów UPnP pomija znane adaptery VPN oraz interfejsy tunelowe. Jest to filtr interfejsów, a nie test osiągalności z internetu. Nieznany adapter lub nietypowy routing nadal wymaga sprawdzenia.

**Aktualizuj oba komputery, uruchom ponownie Minecrafta, utwórz nowe Party i skopiuj nowe zaproszenie.** Samo zaktualizowanie gościa nie poprawi adresu zapisanego w starym kodzie. Nie trzeba wyłączać Hamachi ani innych programów.

Przycisk **Party → Sieć…** otwiera raport z wersją moda, etapem próby, portami TCP i licznikami hosta. Po próbie dołączenia wybierz **Odśwież → Kopiuj raport** na obu komputerach. Raport nie zawiera IP, kodu zaproszenia, identyfikatorów kont ani pełnych komunikatów wyjątków. Nie wysyłaj nikomu pełnego zaproszenia w ramach diagnostyki.

- Błąd TCP i brak wzrostu `Accepted TCP` na hoście podczas kontrolowanej próby wskazują na problem przed listenerem: adres, mapowanie, router, zaporę lub trasę. Sam raport nie rozstrzyga, który z nich.
- Udane TCP i błąd TLS oddzielają negocjację szyfrowania od braku połączenia TCP. Pinning i sprawdzanie dat certyfikatu pozostają włączone.
- `Established TLS` oraz odmowa lobby wskazują na dalszy etap, np. nieaktualne zaproszenie lub limit. Liczniki są sumowane od utworzenia Party i mogą uwzględniać także inne próby.

Komunikat „Internet invitation ready” nadal oznacza wyłącznie przygotowanie zaproszenia i ekspozycji portu. Nie potwierdza dostępności z laptopa. Instrukcja rozpoznawania błędu: [docs/NETWORK_TROUBLESHOOTING.md](docs/NETWORK_TROUBLESHOOTING.md).

## Co zawiera implementacja

- Lobby niezależne od świata: tworzenie, zaproszenie, host, lista i statusy, limit, opuszczanie, wyrzucanie.
- Synchronizacja autorytatywnego stanu przez osobny kanał TLS 1.3; ograniczone kolejki, timeouty i heartbeat.
- Kod `mp2:` z bezpośrednimi kandydatami lub `mp3:` z domeną relaya, identyfikatorem lobby, skrótem certyfikatu, 256-bitowym sekretem i terminem ważności. Kod nie jest wyświetlany jawnie w UI; służy do kopiowania do schowka.
- Zaproszenie ważne godzinę; przycisk unieważnienia generuje nowe. Wyrzucenie gracza również zmienia zaproszenie. Pozostali członkowie zachowują własne poświadczenia.
- Ponawianie utraconego połączenia lobby do około 110 sekund i rezerwacja miejsca przez 120 sekund, bez zapisywania sekretów na dysk.
- Tunel zwykłego protokołu Minecrafta: zintegrowany serwer hosta i lokalny punkt połączenia gościa nasłuchują na `127.0.0.1`. W trybie relay także listener TLS hosta jest wyłącznie lokalny, a połączenia przychodzą przez wychodzący QUIC. W bezpośrednim IPv4/LAN listener TLS przyjmuje połączenia z sieci.
- Weryfikacja pierwszych pakietów logowania wiąże nazwę i UUID z uczestnikiem tunelu. Dalsze uwierzytelnienie i całą synchronizację świata wykonuje Minecraft. Dla świata hosta stan „w świecie” potwierdza zdarzenie vanilla PLAY; na zewnętrznym serwerze klient zgłasza swój stan po jego lokalnym zdarzeniu PLAY.
- Sprzątanie tuneli przy zamknięciu świata, wyjściu, utracie połączenia i wyrzuceniu; obsługa kolejnych światów.

## Ograniczenia

- **Brak ICE, STUN, hole punchingu, PCP, NAT-PMP i TURN w tym wydaniu.** Nowy relay e4mc to odrębny transport QUIC/TCP zewnętrznej usługi. Opcjonalne UPnP nie pokonuje zewnętrznego CGNAT ani podwójnego NAT i wymaga czasowych mapowań TCP. Zwykłe prywatne zaproszenia z trybu LAN nadal nie pozwalają łączyć się z innej sieci.
- Dwa kody offer/answer zostały zaakceptowane jako przyszły sposób sygnalizacji ICE. Aktualny bezpośredni transport używa jednego zaproszenia.
- Najnowsza stabilna wersja wykazana przez Fabric w dniu 2026-11-15 to **26.3**. Środowisko nie mogło pobrać zależności, dlatego artefakt jest wyłącznie dla **26.2**, której biblioteki były dostępne lokalnie. Port na 26.3 nie jest wykonany. Samo przestawienie numeru w metadanych nie wystarczy.
- Nie wykonano testów rozgrywki na dwóch instancjach/komputerach, testów LAN między komputerami, internetu, NAT ani Windows. UI i mixiny wymagają testu w uruchomionym kliencie.
- Zamknięcie aplikacji hosta kończy całe lobby; brak migracji hosta, trwałej listy znajomych i odzyskiwania lobby po restarcie.
- Powrót sieci nie przenosi aktywnego strumienia TCP: gra musi zalogować się ponownie. Nie jest to bezszwowa migracja połączenia.
- Istniejące ręczne udostępnianie LAN jest wykrywane i odrzucane przez most. Utwórz Party przed uruchomieniem świata albo zamknij wcześniejsze udostępnianie.
- Zintegrowany serwer zachowuje zasady logowania vanilla, w tym jego własne zachowanie podczas awarii usług sesji. Mod nie ustawia trybu offline ani nie zastępuje weryfikacji konta.

## Budowanie

W normalnym środowisku z siecią i pełnym JDK 25:

```sh
./gradlew build
# Windows:
# gradlew.bat build
```

To uruchamia także testy `party-core`, w tym prawdziwe gniazda loopback. Zestaw CI dla Windows/Linux jest przygotowany, ale nie został uruchomiony. Sam rdzeń można sprawdzić poleceniem `./gradlew :party-core:test`.

W środowisku tej sesji Gradle zatrzymuje się na tworzeniu lokalnego gniazda (`Operation not permitted`). Użyta jawna ścieżka zastępcza, korzystająca wyłącznie z bibliotek obecnych w cache Gradle:

```sh
python3 tools/build_offline.py --jdk /ścieżka/do/jdk-25 --skip-network-tests
```

Skrypt kompiluje źródła moda i rdzenia, wykonuje testy bez gniazd i pakuje zwykły JAR Fabric z klasami rdzenia. Standardowy Gradle osadza rdzeń jako zagnieżdżony JAR. Skrypt zastępczy **nie jest dowodem udanego builda Gradle** i nie pobiera bibliotek. Uruchomienie go bez `--skip-network-tests` próbuje wykonać także testy gniazd.

Wynik sesji: **65 testów bez gniazd zakończonych sukcesem**, w tym rzeczywisty handshake TLS przez `SSLEngine` w pamięci, dekoder Netty z fragmentami routingu/TLS i wznowieniem manualnego odczytu oraz reguła menu autodołączania. **4 testy gniazd zablokowane** przez system przy tworzeniu listenera w poprzedniej próbie 0.5.0; nie oznaczamy ich jako zaliczonych. Narzędzia nie wykonały ładowania biblioteki natywnej ani sesji z relayem. Raporty: [docs/TESTING.md](docs/TESTING.md), [docs/validation](docs/validation).

## Dokumentacja

- [Wybór transportu i architektura](docs/ARCHITECTURE.md)
- [Bezpieczeństwo i model zaufania](docs/SECURITY.md)
- [Testy i plan sprawdzenia dwóch klientów](docs/TESTING.md)
- [Stan etapów i dalsze prace](docs/ROADMAP.md)

Kod Party jest na licencji MIT. Wbudowana biblioteka Java Netty QUIC zachowuje Apache-2.0, a natywne quiche/BoringSSL własne licencje; teksty są dołączone do JAR-a. Minecraft i Fabric zachowują własne licencje. Nie dołączono plików gry ani bibliotek Essential.
