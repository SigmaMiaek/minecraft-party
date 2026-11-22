# Sześciocyfrowe kody przez Cloudflare — 0.6.0

## Wdrożenie bez instalowania narzędzi na komputerze

1. Załóż konto na <https://dash.cloudflare.com/sign-up> i potwierdź e-mail. Pozostań przy **Workers Free**; nie dodawaj domeny ani płatnego planu.
2. W panelu wybierz **Workers & Pages → Create application → Import a repository / Connect GitHub**. Autoryzuj dostęp do repozytorium `SigmaMiaek/minecraft-party` (wystarczy to jedno repozytorium).
3. Wybierz gałąź `main` i ustaw:

| Pole | Wartość |
| --- | --- |
| Worker / project name | `minecraft-party` |
| Root directory | `cloudflare` |
| Build command | `npm test` |
| Deploy command | `npm run deploy` |

4. Kliknij **Deploy**. Konfiguracja zawiera wiązanie `DIRECTORY` i migrację SQLite; nie twórz ręcznie osobnej bazy. Jeśli panel pyta o subdomenę `workers.dev`, wybierz swoją nazwę. Cloudflare obsługuje token wdrożenia we własnym panelu — nie wklejaj go do moda, repozytorium ani rozmowy.
5. Skopiuj publiczny adres, np. `https://minecraft-party.twoja-nazwa.workers.dev`. Otwórz go z dopiskiem `/health`: poprawna odpowiedź zawiera `"service":"minecraft-party-codes"` i `"protocol":1`. Sam adres `/` może zwrócić 404 i jest to prawidłowe. Do moda wkleja się adres **bez `/health`**.

Jeżeli ekran panelu ma inne nazwy, użyj [oficjalnej instrukcji importu repozytorium](https://developers.cloudflare.com/workers/get-started/dashboard/) oraz [konfiguracji katalogu i poleceń](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/). Projekt jest Workerem, a nie stroną Pages.

## Oba komputery — od 0.7.0

1. Wszyscy instalują **Minecraft Party 0.7.0+26.2** i uruchamiają grę ponownie. Adres wdrożonej usługi jest automatyczny, również w starych pustych konfiguracjach.
2. Repozytorium Cloudflare musi mieć aktualny Worker z `main`. Integracja Git powinna wdrożyć zmianę automatycznie; w razie potrzeby ponów najnowszy build. `/health` zawiera teraz `"autoJoin":true`. Nie trzeba zmieniać bazy ani adresu.
3. Host tworzy Party. W panelu kod jest ukryty domyślnie; oczko go ujawnia, a sąsiednia ikona kopiowania działa też przy ukrytym kodzie. Po zmianie kodu ponownie zostaje ukryty.
4. Znajomy wpisuje 6 cyfr w polach **XXX − XXX**, klika **Dołącz Party** i bez zatwierdzania otrzymuje wewnętrzne zaproszenie do TLS. Działa też wklejanie 123-456. Kod jest teraz samodzielnym uprawnieniem: każdy, kto go zna lub zgadnie, może wejść. Nie ma dodatkowego sprawdzania tożsamości w oknie akceptacji. W świecie nadal obowiązuje logowanie Minecrafta.
5. Lider wybiera świat lub serwer. Gwiazdka obok uczestnika w lobby przekazuje mu lidera; przycisk Opuść Party próbuje przekazać rolę następnej połączonej osobie. [Migracja](LEADERSHIP.md).

Pełne mp2/mp3 pozostają wewnętrznym formatem danych i nie mają pola wklejania ani kopiowania w UI. Nie wyłączono TLS ani weryfikacji odcisku certyfikatu.

## Koszt, zaufanie i ograniczenia

- To backend katalogu zaproszeń; nowe pokoje nie wymagają zatwierdzania. Ruch gry pozostaje w e4mc; usługa nie jest relayem Minecrafta ani integracją zapraszania przez znajomych Mojang.
- Workers Free i SQLite Durable Objects mają darmowe limity. Po ich przekroczeniu operacje mogą przestać działać. Wystarczalność dla większej liczby graczy wymaga pomiarów. [Limity i koszt](https://developers.cloudflare.com/durable-objects/platform/pricing/).
- Administrator usługi i Cloudflare mogą odczytać prywatne zaproszenia, w tym odcisk certyfikatu i sekret dostępu. Ustawiaj wyłącznie adres własnej lub zaufanej usługi. To inny model zaufania niż bezpośrednie przekazanie mp3 poza serwisem.
- Limit prototypu: 256 aktywnych Party, 12 krótkotrwałych próśb na Party, 600 operacji na minutę łącznie i dodatkowe limity na IP i rodzaj operacji. Nie jest to usługa przygotowana na masowe publiczne wdrożenie lub rozproszony atak.
- Host odnawia dzierżawę co około 10 s. Kod znika do 2 minut od utraty hosta; prośba wygasa po maksymalnie 90 s. Uprawnienia katalogu nie przedłużają godzinnej ważności mp3; mod odnawia zaproszenie i kod przed wygaśnięciem. Unieważnienie, wyrzucenie gracza i opuszczenie Party usuwają bieżący przydział, a lokalny sekret lub listener są unieważniane niezależnie od dostępności katalogu.
- Wygasłe rekordy sprzątają zapytania i alarm Durable Object. Nazwy klientów są ograniczone do znaków nicku Minecraft. Zatwierdzenie nie zastępuje uwierzytelnienia Minecrafta.
- Obserwowalność aplikacji jest domyślnie wyłączona; nie logujemy treści zaproszeń, IP, tokenów ani numerów. Cloudflare nadal przetwarza metadane połączeń i dane bazy w ramach świadczenia usługi.

## Weryfikacja

Lokalnie w 0.7.0: kompilacja źródeł Java i 84 testy bez gniazd przez narzędzie awaryjne; 14 scenariuszy polityki katalogu wykonanych w V8. Migracja TLS ma dodatkowy test sieciowy w CI. Gradle lokalnie blokuje ograniczenie dostępu do interfejsów sieciowych. Workflow GitHub dodatkowo buduje i testuje mod na Windows/Linux, testuje Worker z rzeczywistym lokalnym runtime Wrangler i SQLite oraz sprawdza pakowanie `--dry-run`. Wynik konkretnego przebiegu należy sprawdzić w Actions.

Nie wykonano tu wdrożenia na konto użytkownika ani testu nowej ścieżki kodów w dwóch uruchomionych klientach Minecraft. `/health` potwierdza wyłącznie dostępność aplikacji; testem nowej funkcji jest utworzenie kodu, dołączenie bez zatwierdzania, lobby, przejęcie lidera i wejście do świata na dwóch komputerach.

## Wdrożenie użytkownika — 22 listopada 2026

Użytkownik wdrożył aplikację pod adresem `https://minecraft-party.wojciechowskaola57.workers.dev`. Log Cloudflare potwierdził udane przesłanie aplikacji, wiązanie `DIRECTORY (PartyDirectory)` i zakończenie wdrożenia. Użytkownik odczytał `/health` i przesłał odpowiedź `{"service":"minecraft-party-codes","protocol":1}`. Narzędzia tej sesji nie mogły samodzielnie otworzyć tego adresu. Nie potwierdzono jeszcze nowej ścieżki zatwierdzania na dwóch klientach Minecraft.

W 0.6.0 adres trzeba było ustawić ręcznie. Od 0.6.1 jest domyślny i stare puste konfiguracje są migrowane automatycznie.
