# Bezpieczeństwo prototypu

## Model zaufania

Host jest autorytetem lobby i właścicielem świata. Zaproszenie jest sekretem typu bearer: osoba posiadająca cały kod może ubiegać się o miejsce, dopóki kod jest aktualny. Należy przesyłać go prywatnym, uwierzytelnionym kanałem. Podmiana całego zaproszenia przez atakującego jest podmianą tożsamości hosta; pinning nie naprawia przejętego komunikatora.

Dołączenie do lobby uwierzytelnia posiadanie zaproszenia, a wznowienie — indywidualnego losowego poświadczenia 256-bitowego. Nazwa i UUID w samym lobby są deklaracjami klienta. Nie przedstawiamy ich jako zweryfikowanych kont Microsoft. Właściwe logowanie do świata wykonuje standardowy serwer Minecrafta. Nie ustawiamy `usesAuthentication=false`, nie generujemy sesji Microsoft i nie podmieniamy kluczy konta.

Bezpośredni peer zna adres drugiego końca połączenia. W trybie relay operator e4mc zna adresy i metadane obu stron. UI informuje o tym przed dołączeniem i nie wyświetla jawnych adresów. Base64 w kodzie nie szyfruje adresów/domeny ani sekretu: posiadacz kodu może go odczytać. Ukrycie kodu w interfejsie nie zapewnia anonimowości sieciowej. Kopiowanie celowo zapisuje sekret w schowku systemowym; historia schowka może go przechować.

## Zaimplementowane granice

- TLS 1.3 z efemerycznym kluczem ECDSA P-256 hosta i przypięciem SHA-256 samopodpisanego certyfikatu. Prywatne klucze i poświadczenia pozostają w pamięci. Używamy implementacji JDK, nie własnego szyfru.
- Certyfikat sprawdza okres ważności i podpis, a klient odrzuca niewłaściwy odcisk. Zaproszenia wygasają godzinę po utworzeniu lub rotacji; certyfikat jest ważny 7 dni. Długowieczne lobby po wygaśnięciu certyfikatu trzeba utworzyć ponownie.
- Zaproszenie i indywidualne poświadczenie wznowienia są rozdzielone. Rotacja nie wyrzuca pozostałych uczestników. Kick usuwa członka, blokuje jego UUID w tym lobby i rotuje zaproszenie.
- Tunel wymaga aktywnego kontrolnego członka, jego poświadczenia i bieżącego identyfikatora świata. Uczestnik może mieć jeden tunel. Nie można wybrać zdalnie adresu docelowego: wskazuje go wyłącznie host i musi być loopback.
- Weryfikacja pierwszego handshake i login-start ogranicza cel do standardowego logowania Minecrafta i sprawdza nazwę/UUID względem członka. Chroni m.in. przed wysłaniem nazwy właściciela z innego tunelu. Nie zastępuje dalszej weryfikacji sesji vanilla.
- Dla zintegrowanego świata status „w świecie” potwierdza zdarzenie serwera po logowaniu PLAY. Na zewnętrznym serwerze gość zgłasza stan po swoim zdarzeniu PLAY, ponieważ host Party nie jest administratorem tego serwera. Ten status nie nadaje uprawnień.
- Serwer gry nasłuchuje na IPv4 loopback. Nie wystawiamy go przez standardowe otwarcie na LAN. Gość również udostępnia proxy tylko lokalnie. W trybie e4mc także listener TLS hosta jest na loopback i otrzymuje strumienie z wychodzącego QUIC.
- Ścisłe limity wersji, długości zaproszenia, pól, listy kandydatów, uczestników, pakietów przyjęcia i wartości enum. Brak Java ObjectInputStream, zdalnego wykonywania kodu i rozpakowywania nieograniczonych payloadów.
- Maksymalnie 48 obsługiwanych połączeń TLS/tuneli, limit wiadomości kontrolnych na połączenie, kolejka snapshotów pojemności 1, timeout handshake i heartbeat. Strumień gry ma backpressure gniazd i bufor 32 KiB na kierunek.
- Brak logowania pełnych kodów, poświadczeń i adresów w komunikatach błędów UI.

## Granice i otwarte ryzyka

To prototyp bez audytu bezpieczeństwa. Ograniczenie liczby gniazd nie stanowi pełnej ochrony przed DoS. Ktoś w tej samej sieci może czasowo zająć sloty handshake; nie ma limitu adresów źródłowych ani limitu pasma. Nie stosujemy bezwarunkowego `trustAll`.

Członek może przekazać zaproszenie dalej. Po kicku nie obiecujemy trwałego bana rzeczywistej osoby: ponowne uzyskanie nowego kodu i nowej tożsamości może pozwolić na próbę ponownego przyjęcia. Domyślnie goście nie otrzymują uprawnień komend od mostu Party. Inne mody mogą zmienić reguły uprawnień i logowania.

Procesy lokalne z dostępem do konta systemowego, pamięci lub loopback są poza modelem ochrony tego prototypu. Pośrednik TCP zmienia adres widziany przez serwer vanilla na lokalny; mechanizmy banowania/ograniczeń oparte wyłącznie na IP nie odróżnią uczestników. Preferuj UUID i mechanizmy Party.

Zintegrowany serwer zachowuje własny kod logowania i własne zachowanie przy awarii usług uwierzytelniania. To nie jest dodatkowa gwarancja weryfikacji konta ponad vanilla. Ochrona przed złośliwym hostem, migracja hosta i pełne zarządzanie tożsamością lobby nie są rozwiązane tym wydaniem. Internetowy transport e4mc jest prototypem bez potwierdzenia testem rzeczywistej sesji; zmiana adresu albo utrata domeny nadal może zakończyć lobby.


## Czat i wspólny serwer w 0.2.0

Czat jest prywatną funkcją lobby, nie podpisanym czatem Minecrafta. Autor wiadomości pochodzi z aktywnego kontrolnego członka, tekst ma limit 256 znaków i nie zawiera znaków sterujących ani kodów formatowania. Tempo ograniczono do jednej wiadomości na 750 ms na uczestnika, a historia do 64 wiadomości. Tekst jest renderowany jako zwykły tekst bez wykonywania poleceń lub interpretacji linków.

Host z włączonym `shareMultiplayer` wybiera serwer, na który mod automatycznie próbuje połączyć członków Party. Docelowy adres jest udostępniony członkom w szyfrowanym stanie, a nie ukrywany przed nimi kryptograficznie. UI pokazuje rodzaj sesji zamiast jawnego adresu. Walidujemy nazwę/adres i port, odrzucamy URL, znaki sterujące, loopback, multicast i adresy nieokreślone. Publiczny DNS rozwiązuje później standardowy klient Minecrafta.

Zaufanie do hosta obejmuje wybór serwera. Każdy uczestnik loguje się tam samodzielnie; Party nie nadaje dostępu do whitelisty ani uprawnień administracyjnych. Ta funkcja nie rozwiązuje NAT kanału lobby. Wspólne granie na serwerze publicznym nadal wymaga wcześniejszego, działającego połączenia Party.


## Uprawnienia świata 0.3.0

`WorldPermissionPolicy` jest lokalną polityką hosta, przypisaną do jednej instancji zintegrowanego serwera. Komunikacja lobby nie ma wiadomości nadającej OP ani zmieniającej /tp. Host wybiera UUID istniejącego członka, a przed nadaniem OP serwer potwierdza obecność jego rzeczywistego obiektu gracza po logowaniu. Nadanie nie zmienia `ops.json`.

Dwa wąskie hooki vanilla uznają członka z nadaniem za operatora i zapewniają mu poziom GAMEMASTER, zachowując wyższe bazowe uprawnienia. Lista operatorów Party znika po zamknięciu świata albo usunięciu członka. Nie odbieramy uprawnień nadanych wcześniej poza Party.

Przełącznik /tp rozszerza wyłącznie predykaty komend `teleport` i jej aliasu `tp`. Nie podnosi poziomu operatora ani nie odblokowuje innych korzeni komend. Również wymaganie uprawnienia do selektorów @ pozostaje bez zmian. Po każdym przełączeniu serwer odświeża uprawnienia i drzewo komend graczy. Globalny przełącznik /tp obejmuje cały vanilla teleport z nickami/współrzędnymi, w tym przenoszenie innych graczy wskazanych w komendzie.

Hooki działają tylko dla identycznej instancji serwera hosta przechowywanej w aktywnej sesji. Zewnętrzny multiplayer i inne światy nie są objęte tymi nadaniami. Zmiana dotyczy uprawnień do komend, bez modyfikowania uwierzytelniania Minecrafta.


## UPnP i publiczny listener 0.4.0

Tryb Internet wystawia wyłącznie listener TLS Party, z jego zaproszeniem, przypięciem certyfikatu, poświadczeniami członków i limitami. Zintegrowany serwer Minecrafta pozostaje na loopback. UPnP jest lokalnym protokołem routera, bez serwisu pośredniego; nie zapewnia dodatkowego uwierzytelnienia bramy poza zaufaniem do LAN.

SSDP LOCATION musi wskazywać liczbowy adres prywatnego IPv4 odpowiadającego urządzenia. Opis, URLBase i controlURL pozostają na tym samym hoście i porcie; nie śledzimy przekierowań ani proxy. Odpowiedzi HTTP mają limit 64 KiB i timeout, XML blokuje DTD/XXE i głębokość powyżej 64. Kandydaci publiczni wykluczają prywatne i specjalne zakresy, w tym 100.64.0.0/10 CGNAT.

Przed mapowaniem sprawdzamy istnienie portu; wpisy innych aplikacji są pomijane. Po mapowaniu i przed odnowieniem/usunięciem sprawdzamy losowy opis sesji, adres wewnętrzny i port. Wymagamy dodatniej, skończonej dzierżawy; żądane 1800 sekund, odnowienie po 900 sekundach. Cleanup jest best effort; po awarii aplikacji mapowanie ma wygasnąć u routera. Złośliwe urządzenie LAN albo niezgodny router nadal może kłamać o swoim stanie. Publiczna osiągalność nie została potwierdzona testem rzeczywistego internetu.

## Diagnostyka i wybór interfejsów 0.4.1

Znane adaptery VPN oraz interfejsy point-to-point i podinterfejsy są pomijane w kandydatach i odkrywaniu UPnP. Rozpoznanie opiera się na właściwościach i nazwie interfejsu; nie jest dowodem routowalności pozostałych adresów. Po zmianie wyboru trzeba odtworzyć Party. Nie modyfikujemy konfiguracji VPN, routingu ani zapory.

Raport diagnostyczny zawiera wersję moda, systemu i Javy, czas UTC, rolę, status, numery portów oraz liczniki. Ślad klienta ma maksymalnie 32 zdarzenia z numerem kandydata, etapem i kodem błędu. Nie przyjmuje adresów, poświadczeń, nazw/UUID graczy ani treści komunikatów wyjątków. Logi nowej diagnostyki zawierają tylko tryb, porty i kody stanu. Raport trafia do schowka dopiero po naciśnięciu przycisku kopiowania.

Klasyfikacja błędów nie zmienia polityki TLS: certyfikat nadal musi mieć właściwy odcisk, podpis i okres ważności. Błąd certyfikatu nie uruchamia nieszyfrowanego transportu ani alternatywnego zaufania.

## Publiczny relay i biblioteka natywna 0.5.0

Publiczny e4mc jest wybranym i zaakceptowanym przez użytkownika pośrednikiem. Broker odpowiada przez HTTPS, ograniczamy długość JSON i domenę węzła do `e4mc.link`. QUIC używa weryfikacji zaufania i nazwy relaya. Osobne TLS Party przypina certyfikat hosta z zaproszenia: relay nie otrzymuje jego klucza prywatnego. W jawnej części występuje jedynie prefiks routingu Minecraft z domeną, bez nazwy/UUID gracza czy sekretu zaproszenia. Nie pokazujemy broadcastów relaya jako zaufanych komunikatów UI.

Przydzielona domena nie otwiera zintegrowanego serwera gry dla klientów bez poświadczeń Party. Prefiks jest najpierw walidowany i usuwany, potem wymagany jest dotychczasowy TLS i sekret/wznowienie członka. Ruch jest ograniczony liczbą strumieni, timeoutami i backpressure. Relay nadal może przerwać sesję albo obserwować metadane; nie zapewniamy jego dostępności ani anonimowości.

Wbudowany kod Java Netty QUIC ma przypięte źródło i dołączone licencje. Natywna biblioteka platformy jest pobierana przez HTTPS z `natives.e4mc.link`; dozwolone są HTTPS redirecty CDN bez downgrade. Cały body ma limit 64 MiB i timeout 45 sekund. Przed zapisem i przed załadowaniem sprawdzamy SHA-256 z przypiętego źródła, a zapis używa pliku tymczasowego i zastąpienia cache. Parametry systemowe zmieniające URL lub ścieżkę natywnej biblioteki z oryginalnej implementacji są wyłączone. `System.load` następuje wyłącznie dla zweryfikowanego pliku. Cache nie przechowuje poświadczeń lobby. Integralność pobrania nie zastępuje audytu kodu natywnego.

Więcej: [E4MC_RELAY.md](E4MC_RELAY.md), [pochodzenie źródeł](../vendor/netty-quic/UPSTREAM.md). Żadne połączenie z publicznym relayem ani ładowanie JNI nie zostało wykonane w tym sandboxie.
