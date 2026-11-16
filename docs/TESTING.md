# Weryfikacja — 2026-11-16, wydanie 0.5.2

## Wyniki rzeczywiście wykonane

- Cały kod Java moda i rdzenia skompilowany `javac --release 25` przeciw rzeczywistemu Minecraftowi 26.2 i lokalnym bibliotekom Fabric.
- JAR Fabric wygenerowany skryptem offline, zawiera klasy moda/rdzenia, metadane, mixiny i lokalizacje.
- 65 testów JUnit zakończonych sukcesem: poprzednie 61 oraz wznowienie odczytu po prefiksie bez TLS, reguła zwykłych/niestandardowych menu, ochrona innych przepływów połączenia/świata i właściwy komunikat błędu TLS przez relay.
- Testy gniazd faktycznie podjęte: 4 zatrzymane przez `java.net.SocketException: Operation not permitted` przy otwieraniu listenera. Nie wliczamy ich do zaliczonych.
- Gradle build podjęty: zatrzymany podczas tworzenia usługi blokad używającej gniazda, zanim uruchomił kompilację.

Surowe wyniki są w [validation/offline-build.txt](validation/offline-build.txt), [validation/socket-tests.txt](validation/socket-tests.txt) oraz [validation/gradle-build.txt](validation/gradle-build.txt). Nie wykonano obejścia ograniczeń systemowych.

Poprzednia pełna próba 0.5.0 jest w [validation/socket-tests-0.5.0.txt](validation/socket-tests-0.5.0.txt): 64 testy znalezione, 60 zaliczonych, 4 testy gniazd przerwane `Operation not permitted`. Przy poprawce UI nie ponawiano testów gniazd, których blokada pozostaje. Starszy `socket-tests.txt` jest historycznym raportem 0.2.0. Ani te testy, ani statyczne sprawdzenie JAR nie nawiązują sesji z publicznym e4mc.

## Testy automatyczne do wykonania poza sandboxem

```sh
./gradlew build
./gradlew :party-core:test --tests '*TransportIntegrationTest'
```

`TransportIntegrationTest` używa rzeczywistych gniazd loopback: synchronizacja lobby i rotacja kodu, kick oraz duży strumień przez TLS w dwóch kolejnych epokach świata. Backend w tym konkretnym teście to serwer bajtowy sprawdzający prefiks, a nie Minecraft — sukces testu nie potwierdza logowania do Minecrafta, chunków ani zachowania mixinów.

## Dwa klienci Minecraft — niewykonane

Użyj dwóch osobnych instancji i dwóch różnych kont z prawidłową sesją Minecraft, obu z Fabric API i modem. Dla developmentu `./gradlew runClient -PdevUsername=PartyHost` pozwala zmienić nazwę, ale nie tworzy legalnej sesji konta i nie zastępuje testu multiplayer. Host i gość muszą używać zgodnych wersji gry i pozostałych modów.

1. W menu hosta utwórz Party o limicie 6, skopiuj kod; drugi klient dołącza pozostając w menu. Sprawdź hosta, skład i status.
2. Host wybiera istniejący świat survival. Oczekiwane: brak publicznego listenera vanilla, gość automatycznie rozpoczyna normalne logowanie, obie strony grają.
3. Sprawdź chodzenie, niszczenie/stawianie bloków, ekwipunek, crafting, drop, encje, śmierć/respawn, Nether/End i ładowanie chunków. Świat synchronizuje vanilla, nie nowy silnik.
4. Dodaj kolejnego gościa do już uruchomionego świata. Sprawdź limit obejmujący hosta i odmowę nadmiarowemu uczestnikowi.
5. Otwórz ESC i ekran Party. Gra hosta nie może zatrzymać ticków dla pozostałych.
6. Host zamyka świat. Oczekiwane: goście wracają do Party, lista pozostaje. Host uruchamia drugi świat creative; wszyscy ponownie dołączają.
7. Wyrzuć gościa podczas gry. Sprawdź zakończenie tunelu, usunięcie z listy i odrzucenie starego kodu. Pozostali uczestnicy pozostają w lobby.
8. Unieważnij zaproszenie. Stare zaproszenie nie przyjmuje nowych osób; istniejący uczestnik nadal może wznowić połączenie.
9. Przerwij sieć gościa na 10–30 sekund i przywróć bez zmiany adresacji. Sprawdź status rozłączony, wznowienie lobby i ponowne logowanie do gry. Powtórz z przerwą ponad 120 sekund.
10. Zamknij hosta, rozłącz gościa ręcznie, anuluj ConnectScreen i sprowokuj błąd logowania. Nie może powstać pętla automatycznych prób; Party ma pozostać dostępne.
11. PL/EN, małe okno 320×240 w jednostkach GUI, różne skale, stronicowanie 8 graczy, długie nazwy, nawigacja klawiaturą. Obejrzyj czytelność ostrzeżenia IP.
12. Powtórz na Windows i Linux; sprawdź zamknięcie gniazd po opuszczeniu lobby i brak otwartego portu vanilla na publicznym interfejsie.

## Internet/NAT — niewykonane, implementacja przechodzenia NAT niegotowa

Po wdrożeniu ICE trzeba sprawdzić dwie różne sieci z publicznym IPv4, zwykłym NAT, podwójnym NAT, CGNAT, symetrycznym mapowaniem, blokadą UDP, IPv6 i zmianą sieci w trakcie sesji. Oczekiwany wynik części przypadków bez TURN to jawny błąd zestawienia. Należy rejestrować rodzaj wybranej pary kandydatów bez publikowania adresów i sekretów. Sam test STUN nie jest testem gry przez internet.


## Dodatkowe testy 0.2.0 — ręczne niewykonane

- Porównaj szeroki ekran Party ze szkicem użytkownika: lewa lista z głowami i licznikiem, środkowy czat, prawa sekcja hosta, przyciemniony dirt zamiast zielonego wypełnienia. Sprawdź zakładki w 320×240 i trzy kolumny przy 640×360, również z ośmioma członkami.
- Wróć do menu głównego z aktywnym Party. Przy wystarczającej szerokości lista/czat mają być po prawej bez zasłaniania vanilla Multiplayer, Realms i opcji. W węższym oknie powinien pozostać przycisk Party bez nachodzącego panelu.
- Wyślij wiadomości z obu klientów w obu ekranach; sprawdź Enter, Wyślij, fokus po wysłaniu, przewijanie i 256 znaków. Zmień świat i wróć do lobby — historia tej samej Party powinna pozostać.
- Host z opcją Wspólny serwer: WŁ. wchodzi na dostępny serwer zewnętrzny. Gość ma połączyć się samodzielnie z tą samą domeną i portem po faktycznym wejściu hosta. Powtórz z domeną SRV i niestandardowym portem.
- Sprawdź odmowę dostępu gościowi przez whitelistę / pełny serwer. Party nie może obchodzić błędu ani zapętlać prób. Przyciskiem w Party można ponowić próbę.
- Host opuszcza serwer, wyłącza udostępnianie lub wybiera kolejny świat/serwer. Sprawdź pozostawienie składu i czatu oraz nową epokę sesji. Zaproszony później gość powinien poznać bieżący cel.

Automatyczne testy nowego modelu, walidacji adresów, historii/tempa czatu, zgodności kodów i kodowania snapshotów są zaliczone (łącznie 22 testy bez gniazd). Czwarty test transportowy dodaje czat i synchronizację celu przez prawdziwy TLS; jego wykonanie, tak jak pozostałych testów gniazd, zatrzymuje systemowa blokada.


## Testy 0.3.0

Osiem nowych testów `WorldPermissionPolicyTest` sprawdza: domyślny brak uprawnień, wyłączną kontrolę hosta, niezależność /tp od OP, zachowanie OP po wyłączeniu /tp, odebranie praw po kicku, zamknięcie i niezależność światów oraz niemodyfikowalność snapshotów. Łącznie 30 testów bez gniazd zaliczonych. Walidator artefaktu dodatkowo sprawdza istnienie metod `PlayerList.isOp`, `MinecraftServer.getProfilePermissions` oraz obu predykatów w `TeleportCommand.register` w rzeczywistym 26.2. To statyczna weryfikacja celów mixinów, nie test działania komend w grze.

Ręczne testy 0.3.0 do wykonania na dwóch zalogowanych klientach:

1. Otwórz ESC: po prawej lista bez czatu, przyciski menu po lewej, nadal działające ustawienia/osiągnięcia/wyjście. Sprawdź m.in. szerokość 464 jednostek GUI i bardzo małe okno z fallbackiem do przycisku Party.
2. W Party nie powinno być przycisków Singleplayer i Multiplayer. Wróć do zwykłego menu, uruchom świat i sprawdź automatyczne dołączanie.
3. Bez OP i bez przełącznika /tp gość nie powinien zyskać żadnej nowej komendy. Włącz /tp: `/tp Nick` i `/tp x y z` powinny działać, a `/give`, `/gamemode`, `/execute` pozostać niedostępne. Wyłącz /tp i sprawdź odebranie uprawnienia oraz aktualizację podpowiedzi.
4. Nadaj OP+ konkretnemu zalogowanemu gościowi: sprawdź komendy poziomu 2, np. `/gamemode`, a następnie OP− i ich ponowną odmowę. Inny gość nie otrzymuje OP.
5. Wyrzuć operatora, zamknij świat, opuść Party i uruchom inny świat. Nadanie Party nie powinno pozostać ani zapisać się w `ops.json`. Gość z wcześniejszym nadaniem vanilla zachowuje bazowe uprawnienia.
6. Sprawdź zewnętrzny serwer multiplayer: brak działających kontrolek OP i /tp po stronie Party, uprawnienia serwera pozostają pod kontrolą jego administratora.

Te scenariusze w uruchomionym Minecraft nie zostały tutaj wykonane. Raport `socket-tests.txt` pochodzi z transportu 0.2.0; nie stanowi testu nowych uprawnień. Transport i format mp2 nie zostały w 0.3.0 zmienione.


## Testy 0.4.0 — 41 testów bez gniazd zaliczonych

Osiem dodatkowych testów `UpnpProtocolTest` sprawdza walidację odpowiedzi SSDP, namespace i URLBase, blokadę przekierowania na inny host/port, XXE/rozmiar/głębokość XML, escaping SOAP, brak i duplikację wartości, własność/dzierżawę mapowania oraz odrzucanie adresów prywatnych/CGNAT/specjalnych. Nie jest to test prawdziwego routera ani dowód zestawienia połączenia internetowego.

Do wykonania poza tym środowiskiem:

1. Host za routerem z publicznym IPv4 i IGD: utwórz Internet Party, potwierdź tymczasowy wpis TCP wskazujący wyłącznie listener TLS, a następnie dołącz z drugiej sieci przez całe mp2. Sprawdź lobby i świat.
2. Sprawdź różne porty wewnętrzny/zewnętrzny oraz istniejące mapowanie innej aplikacji — mod nie może go nadpisać ani usunąć.
3. Po 15 minutach sprawdź odnowienie dzierżawy. Opuść Party i sprawdź usunięcie własnego wpisu. Przerwij aplikację i sprawdź wygaśnięcie wpisu u routera.
4. Wyłącz UPnP, użyj CGNAT/podwójnego NAT i routera dopuszczającego tylko wpisy bezterminowe: Internet Party powinno zakończyć tworzenie błędem, bez cichego przejścia do LAN.
5. Wybierz jawnie LAN i sprawdź dotychczasowy scenariusz. Anuluj tworzenie Internet Party i sprawdź zamknięcie listenera oraz własnego mapowania.

Te testy na routerze i dwóch komputerach nie zostały wykonane. Raport testów gniazd z wcześniejszego transportu nadal dokumentuje blokadę systemową; UPnP również nie da się tu uruchomić przez prawdziwe gniazda.

Trzy testy `InternetExposureTest` dodatkowo sprawdzają publicznego kandydata bez kontaktowania się z nim, rotację sekretu, powiązanie z konkretnym hostem oraz anulowanie/zamknięcie ekspozycji. Nie wykonują połączeń do adresów kandydatów.

## Testy 0.4.1 — 48 testów bez gniazd zaliczonych

Siedem nowych testów `NetworkDiagnosticsTest` sprawdza przypisanie timeoutu do TCP/TLS/lobby, rozróżnienie odmowy i braku trasy, datę i odrzucenie certyfikatu, zachowanie błędu z najdalszego etapu po nieudanych próbach kolejnych kandydatów, ograniczenie raportu do 32 zdarzeń bez treści wyjątków oraz pomijanie Hamachi/Radmin i typowych tuneli przy zachowaniu Ethernet/Wi-Fi/mostu br0. Walidator JAR potwierdza 105 zgodnych wpisów PL/EN. To nie potwierdza połączenia między komputerami.

Zgłoszenie użytkownika dla 0.4.0: oba komputery z Linux, różne sieci, tryb Internet (P2P), host pokazuje „Internet invitation ready”, laptop zgłasza „Host unreachable”. Lokalnie znaleziono interfejs `ham0`. W kodzie wykazano możliwość potraktowania jego adresu jako publicznego i pominięcia UPnP. Nie sprawdzano pełnego zaproszenia użytkownika; nie potwierdzono, że jest to jedyna przyczyna tej próby.

Do wykonania na obu komputerach po aktualizacji do 0.4.1:

1. Uruchomić ponownie Minecrafta, utworzyć nowe Party i nowe zaproszenie. Sprawdzić, czy tryb zgłaszany w raporcie to UPNP, DIRECT_IPV4, czy tworzenie kończy się jawnym błędem.
2. Spróbować dołączyć z drugiej sieci. Na obu komputerach otworzyć Sieć…, odświeżyć i skopiować raport. Powiązać czas próby i zmianę liczników hosta z etapem u gościa.
3. Sprawdzić poprawne dołączenie oraz kontrolowane przypadki: zamknięty port, wygasłe zaproszenie, inne lobby, zła data certyfikatu i rozłączenie po uzyskaniu TLS. Nie wyłączać pinningu na potrzeby testu.
4. Przy aktywnym Hamachi powtórzyć tworzenie Internet Party bez zmiany konfiguracji samego VPN. Sprawdzić, czy wybierana jest rzeczywista brama, a nie adres adaptera.

W tym środowisku nie można otwierać gniazd ani odczytać stanu firewalld przez D-Bus. Nie zmieniono zapory. Ekran diagnostyki i skuteczność poprawki w prawdziwym połączeniu pozostają do sprawdzenia przez użytkownika. Instrukcja: [NETWORK_TROUBLESHOOTING.md](NETWORK_TROUBLESHOOTING.md).

## Testy 0.5.0 — 60 testów bez gniazd zaliczonych

Osiem nowych testów rdzenia sprawdza ograniczenie domen do e4mc, dokładny prefiks routingu i odrzucanie złego/uciętego, kontrolę długości varint, mp3 bez adresu hosta z zachowaniem poświadczeń TLS, zgodność mp2, rozróżnienie wersji/transportu, wygaśnięcie i limit HTTP podczas odbioru. Cztery testy w `src/test` używają rzeczywistego `EmbeddedChannel`: pierwszy rekord TLS po prefiksie w tym samym buforze, fragmentacja po jednym bajcie, błędny cel oraz zbyt długi/niekanoniczny prefiks długości. Nie korzystają z testowych atrap połączenia w produkcyjnym kodzie ani z gniazd/JNI.

Do wykonania poza sandboxem:

1. Świeża instancja: host tworzy Party e4mc, sprawdza pobranie właściwej biblioteki, jej SHA-256 i brak powtórnego pobierania przy kolejnym tworzeniu. Błędny plik cache musi zostać odrzucony; bez sieci powstaje jawny błąd, bez ładowania niezweryfikowanego kodu.
2. Host Starlink po Ethernet, gość przez hotspot, dwie legalne sesje kont. Sprawdzić połączenie do lobby przed uruchomieniem świata, czat, pełną rozgrywkę, późnego gościa i zmianę świata przy zachowanym lobby.
3. Potwierdzić listener TLS i serwer gry wyłącznie na loopback w trybie relay, brak UPnP i brak publicznego listenera gry. Klient bez zaproszenia nie może dostać się do lobby ani świata mimo znajomości domeny relaya.
4. Rotacja zaproszenia/kick: stare mp3 nie dopuszcza nowego klienta, inni członkowie zachowują poświadczenia. Opuszczenie Party zamyka QUIC, przydział i lokalne gniazda. Zamknięcie wyłącznie świata pozostawia tunel lobby.
5. Blokada UDP, niedostępny broker/CDN, brak przydziału i utrata tunelu mają dawać właściwy błąd, bez cichego przejścia na LAN lub pominięcia TLS. Krótka utrata pakietów i zerwanie całej sesji są osobnymi przypadkami; po utracie domeny wymagane jest nowe Party.
6. Powtórzyć na Windows/Linux, skontrolować natywne ładowanie i brak blokowania wątku renderowania. Sprawdzić stary bezpośredni mp2 i cel multiplayer, który wciąż obsługuje vanilla każdego gościa.

**Niewykonane:** pobranie/załadowanie JNI, faktyczne zestawienie QUIC z e4mc, przydział domeny przez publiczną usługę i dwa klienci. Broker/CDN nie są dostępne z narzędzi tej sesji, a Gradle i testy gniazd są blokowane przez środowisko. Liczba 60 odnosi się wyłącznie do testów bez gniazd.

## Testy 0.5.1 — 61 testów bez gniazd zaliczonych

Regresja generuje prawdziwe zaproszenia bezpośrednie mp2 i relayowe mp3, dodaje białe znaki typowe dla schowka i sprawdza ich akceptację przez wspólną funkcję używaną w przycisku Dołącz oraz zgodny wynik pełnego dekodowania. Odrzuca także pusty tekst, sam prefiks, nieobsługiwane wersje i zbyt długi kod. UI odświeża aktywność przy zmianie pola oraz w ticku. Hint PL/EN wymienia oba formaty.

Źródła moda i rdzenia skompilowane, testy bez gniazd zaliczone, statyczna kontrola JAR wykonana. Gradle nadal zatrzymuje się na lokalnej usłudze gniazd. Ręcznie w uruchomionej grze trzeba sprawdzić wklejenie oryginalnego mp3, aktywny przycisk i próbę dołączenia do hosta 0.5.0/0.5.1. Tego testu UI oraz sesji przez internet nie wykonano narzędziami tej sesji.

## Testy 0.5.2 — 65 testów bez gniazd zaliczonych

Nowy test `aRoutingPrefaceWithoutTlsBytesRearmsTheManuallyReadStream` wykazał błąd przed poprawką: strumień AUTO_READ=false konsumował poprawny prefiks, usuwał dekoder i nie żądał kolejnego odczytu na TLS ClientHello. Poprzednie testy samych bajtów w EmbeddedChannel nie sprawdzały kontraktu wywołań read(). Przed poprawką: 62 testy, 61 zaliczonych i 1 oczekiwana porażka; zapis [validation/relay-read-before.txt](validation/relay-read-before.txt). Po poprawce i dodatkowych testach: 65 zaliczonych. To odtworzenie zatrzymania w rzeczywistym pipeline Netty bez gniazd, a nie test publicznego QUIC.

Dwa testy reguły menu obejmują TitleScreen, Party, diagnostykę, ekran rozłączenia, ustawienia oraz obcą klasę menu dziedziczącą bezpośrednio z Screen. Osobno sprawdzają blokadę ConnectScreen, pauzy, postępu, ładowania i tworzenia/wyboru świata. Kod przed rozpoczęciem próby nadal sprawdza brak aktywnego świata, zintegrowanego serwera i innego połączenia. Nie uruchamia to Lunar ani prawdziwego ConnectScreen.

Test komunikatów odróżnia TCP do relaya od dotarcia do hosta i zachowuje błędy certyfikatu. Dodatkowa diagnostyka operuje licznikami i etapami, bez IP, domeny zaproszenia, adresu serwera, nicków i UUID.

Użytkownik zgłosił udane połączenie Party oraz podejrzenie związku z Lunar po wcześniejszym timeout TLS i braku autojoin. Brak raportów obu stron, więc nie uznajemy Lunar za potwierdzoną przyczynę. Do sprawdzenia na obu komputerach po aktualizacji: prefiks i TLS w różnych porcjach, dwukierunkowy czat lobby, start świata, drugi tunel gry, zewnętrzny serwer, zamknięcie i drugi świat oraz menu Lunar z dodatkiem Fabric. Narzędzia tej sesji nie wykonały tych testów, ładowania JNI ani rozgrywki.
