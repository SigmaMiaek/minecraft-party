# Diagnostyka połączenia — 0.4.1

**Aktualizacja 0.5.0:** dla zgłoszonego hosta Starlink i gościa w hotspocie dodano zaakceptowany publiczny relay e4mc. Wybierz **Internet (relay e4mc)** zamiast bezpośredniego IPv4/UPnP i utwórz nowe zaproszenie mp3. Instrukcja i granice weryfikacji: [E4MC_RELAY.md](E4MC_RELAY.md). Dalsza część tej strony opisuje diagnostykę bezpośredniego trybu z 0.4.1.

## Pierwsza próba po aktualizacji

1. Na obu komputerach usuń poprzedni JAR Minecraft Party z `mods`, wstaw `minecraft-party-0.4.1+26.2.jar` i uruchom ponownie Minecrafta 26.2.
2. Host tworzy **nowe** Party w trybie Internet (P2P) i kopiuje **nowe** zaproszenie. Stary kod zawiera stary adres, port i certyfikat.
3. Gość wkleja całe zaproszenie. Po nieudanej próbie obie strony wybierają **Party → Sieć… → Odśwież → Kopiuj raport**.

Nie przesyłaj pełnego zaproszenia jako zgłoszenia błędu. Raport zawiera porty i etapy, bez adresów IP, poświadczeń, nicków i UUID. Odświeżenie jest ręczne, więc wykonaj je po próbie gościa. Porównuj raporty z tego samego Party; po jego ponownym utworzeniu port może się zmienić.

## Co poprawiono

W 0.4.0 mod uznawał publicznie wyglądający adres dowolnego aktywnego interfejsu za kandydata internetowego. Pomijał wtedy UPnP. Hamachi może przydzielić wirtualny adres 25.x.x.x dostępny przez wspólną sieć Hamachi, co opisuje [dokumentacja producenta](https://lmi-documentation-origin.azurewebsites.net/webhelp/EN/CentralUserGuide/LogMeIn/c_hamachi_Virtual_IP.html). Sam test zakresu adresu tego nie wykrywał. To rzeczywisty błąd wyboru, ale bez raportów nie przesądza przyczyny każdego „Host unreachable”.

W 0.4.1 oba miejsca wyboru interfejsu korzystają z filtra znanych adapterów VPN i tuneli. Java `NetworkInterface.isVirtual()` oznacza podinterfejs i sama nie wykrywa wszystkich adapterów VPN, zgodnie z [dokumentacją JDK 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/NetworkInterface.html#isVirtual()). Filtr nie wyłącza programów VPN ani nie zmienia systemowej trasy. Dla nierozpoznanego adaptera lub nietypowego routingu nadal może być potrzebna korekta wyboru interfejsu.

## Jak odczytać wynik

| Wynik gościa i hosta | Co wiadomo | Dalszy krok |
| --- | --- | --- |
| `TCP_TIMEOUT`, `NO_ROUTE` lub `TCP_REFUSED`; `Accepted TCP` hosta nie rośnie w czasie próby | Nie potwierdzono dotarcia do listenera Party | Świeży kod, stan ekspozycji, publiczny IPv4 bramy, UPnP i zapora hosta |
| Gość uzyskał `TCP CONNECTED`, potem `TLS_TIMEOUT` albo `TLS_ERROR` | Otwarto TCP, nie uzyskano poprawnego TLS | Sprawdzić port docelowy, wersje, mapowanie i licznik `Failed TLS`; nie wyłączać weryfikacji certyfikatu |
| `TLS_CERTIFICATE` | Certyfikat został odrzucony | Utworzyć świeże zaproszenie do właściwego Party |
| `TLS_CLOCK` | Data certyfikatu nie została zaakceptowana | Sprawdzić datę i godzinę obu komputerów, potem ponowić z nowym Party |
| `TLS CONNECTED`, potem `LOBBY REJECTED` | Połączenie jest zaszyfrowane, host odmówił przyjęcia | Sprawdzić ważność kodu, limit i wcześniejsze wyrzucenie |
| `LOBBY AUTHENTICATED` | Uwierzytelniono kanał lobby | Osobno sprawdzić rozpoczęcie sesji świata i standardowe logowanie Minecrafta |
| Host ma `internet_unavailable` podczas tworzenia | Po filtrowaniu nie uzyskano obsługiwanej ekspozycji | Sprawdzić publiczny IPv4 i UPnP; CGNAT i podwójny NAT nie są obsługiwane przez ten transport |

Liczniki hosta są sumowane od uruchomienia Party. Mogą obejmować skanowanie portu przez inne urządzenie. Sam `Accepted TCP > 0` nie potwierdza, że konkretnie laptop dotarł do hosta — porównaj zmianę w czasie kontrolowanej próby ze śladem klienta. `Exposure valid` opisuje lokalny stan ekspozycji, a nie pomiar dostępności z internetu. Udane lobby nie jest jeszcze testem rozgrywki.

## Zapora i router

`Local TCP port` to port procesu Java na hoście. `Advertised TCP port` to port wpisany do zaproszenia; UPnP może przydzielić inny port zewnętrzny. Ewentualna reguła zapory systemowej dotyczy portu **lokalnego** TCP. Nie dotyczy portu vanilla 25565: zintegrowany serwer gry pozostaje na loopback.

Jeżeli host używa firewalld, najpierw sprawdź jego stan i aktywną strefę rzeczywistej karty sieciowej. Zmianę ogranicz do tego jednego portu w odpowiedniej strefie; procedurę opisuje [oficjalna dokumentacja firewalld](https://firewalld.org/documentation/howto/open-a-port-or-service.html). UPnP routera nie zmienia reguł zapory systemowej. W tej sesji nie potwierdzono stanu zapory i nie zmodyfikowano jej ustawień.

Prototyp nie zawiera relaya, ICE ani STUN. Gdy ISP stosuje CGNAT albo router nie zapewnia obsługiwanego mapowania, samo nowe zaproszenie nie zapewni połączenia. „Internet invitation ready” oznacza przygotowanie kandydata i ekspozycji; nie jest testem wykonanym z obcej sieci. Żaden test na dwóch komputerach ani prawdziwym routerze nie został wykonany przez narzędzia w tej sesji.
