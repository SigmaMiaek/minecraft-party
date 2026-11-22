# Sześciocyfrowe kody przez Cloudflare — 0.6.0

## Wdrożenie bez instalowania narzędzi na komputerze

1. Załóż konto na <https://dash.cloudflare.com/sign-up> i potwierdź e-mail. Pozostań przy **Workers Free**; nie dodawaj domeny ani płatnego planu.
2. W panelu wybierz **Workers & Pages → Create application → Import a repository / Connect GitHub**. Autoryzuj dostęp do repozytorium `SigmaMiaek/minecraft-party` (wystarczy to jedno repozytorium).
3. Wybierz gałąź `main` i ustaw:

| Pole | Wartość |
| --- | --- |
| Worker / project name | `minecraft-party-codes` |
| Root directory | `cloudflare` |
| Build command | `npm test` |
| Deploy command | `npm run deploy` |

4. Kliknij **Deploy**. Konfiguracja zawiera wiązanie `DIRECTORY` i migrację SQLite; nie twórz ręcznie osobnej bazy. Jeśli panel pyta o subdomenę `workers.dev`, wybierz swoją nazwę. Cloudflare obsługuje token wdrożenia we własnym panelu — nie wklejaj go do moda, repozytorium ani rozmowy.
5. Skopiuj publiczny adres, np. `https://minecraft-party-codes.twoja-nazwa.workers.dev`. Otwórz go z dopiskiem `/health`: poprawna odpowiedź zawiera `"service":"minecraft-party-codes"` i `"protocol":1`. Sam adres `/` może zwrócić 404 i jest to prawidłowe. Do moda wkleja się adres **bez `/health`**.

Jeżeli ekran panelu ma inne nazwy, użyj [oficjalnej instrukcji importu repozytorium](https://developers.cloudflare.com/workers/get-started/dashboard/) oraz [konfiguracji katalogu i poleceń](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/). Projekt jest Workerem, a nie stroną Pages.

## Oba komputery

1. Zainstaluj **Minecraft Party 0.6.0+26.2**, usuń wcześniejszy JAR i uruchom grę ponownie. Wymagania Minecraft/Fabric/Java pozostają takie jak w 0.5.2.
2. Party → **Kody Party…** (również dostępne przez **Sieć…**) → wklej ten sam publiczny adres → **Zapisz**. Zmiana adresu jest możliwa po opuszczeniu Party. Mod akceptuje HTTPS pod `workers.dev`, bez portu, ścieżki, parametrów i przekierowań.
3. Host: utwórz Party w trybie **Internet (relay e4mc)**. Po przygotowaniu relaya i publikacji pojawi się sześciocyfrowy kod. **Kopiuj zaproszenie** kopiuje teraz ten kod; pełne mp3 nadal można skopiować osobnym przyciskiem w Kodach Party.
4. Znajomy wpisuje 6 cyfr (także z zerem na początku) i klika **Dołącz**. Dostanie **numer swojej prośby**. Host otwiera Kody Party, porównuje ten numer ze znajomym przez zaufany komunikator lub rozmowę i klika **Przyjmij** przy właściwej prośbie. Sam nick nie potwierdza tożsamości.
5. Po zatwierdzeniu mod pobiera prywatne zaproszenie i uruchamia istniejące TLS oraz połączenie lobby. Host wybiera świat lub serwer przez zwykłe menu Minecrafta.

Konto Cloudflare zakłada tylko administrator usługi; znajomi go nie potrzebują. Publiczny URL usługi można udostępnić, lecz tokenów, pełnych mp3 i raportów zawierających sekrety nie należy publikować.

## Koszt, zaufanie i ograniczenia

- To opcjonalny backend katalogu i zatwierdzania zaproszeń. Ruch gry pozostaje w e4mc; usługa nie jest relayem Minecrafta ani integracją zapraszania przez znajomych Mojang.
- Workers Free i SQLite Durable Objects mają darmowe limity. Po ich przekroczeniu operacje mogą przestać działać. Wystarczalność dla większej liczby graczy wymaga pomiarów. [Limity i koszt](https://developers.cloudflare.com/durable-objects/platform/pricing/).
- Administrator usługi i Cloudflare mogą odczytać prywatne zaproszenia, w tym odcisk certyfikatu i sekret dostępu. Ustawiaj wyłącznie adres własnej lub zaufanej usługi. To inny model zaufania niż bezpośrednie przekazanie mp3 poza serwisem.
- Limit prototypu: 256 aktywnych Party, 12 krótkotrwałych próśb na Party, 600 operacji na minutę łącznie i dodatkowe limity na IP i rodzaj operacji. Nie jest to usługa przygotowana na masowe publiczne wdrożenie lub rozproszony atak.
- Host odnawia dzierżawę co około 10 s. Kod znika do 2 minut od utraty hosta; prośba wygasa po maksymalnie 90 s. Uprawnienia katalogu nie przedłużają godzinnej ważności mp3; mod odnawia zaproszenie i kod przed wygaśnięciem. Unieważnienie, wyrzucenie gracza i opuszczenie Party usuwają bieżący przydział, a lokalny sekret lub listener są unieważniane niezależnie od dostępności katalogu.
- Wygasłe rekordy sprzątają zapytania i alarm Durable Object. Nazwy klientów są ograniczone do znaków nicku Minecraft. Zatwierdzenie nie zastępuje uwierzytelnienia Minecrafta.
- Obserwowalność aplikacji jest domyślnie wyłączona; nie logujemy treści zaproszeń, IP, tokenów ani numerów. Cloudflare nadal przetwarza metadane połączeń i dane bazy w ramach świadczenia usługi.

## Weryfikacja

Lokalnie: kompilacja wszystkich źródeł Java i 68 testów bez gniazd przez narzędzie awaryjne; 13 scenariuszy polityki katalogu wykonanych w V8. Gradle lokalnie blokuje ograniczenie dostępu do interfejsów sieciowych. Workflow GitHub dodatkowo buduje i testuje mod na Windows/Linux, testuje Worker z rzeczywistym lokalnym runtime Wrangler i SQLite oraz sprawdza pakowanie `--dry-run`. Wynik konkretnego przebiegu należy sprawdzić w Actions.

Nie wykonano tu wdrożenia na konto użytkownika ani testu nowej ścieżki kodów w dwóch uruchomionych klientach Minecraft. `/health` potwierdza wyłącznie dostępność aplikacji; testem funkcji jest utworzenie kodu, zatwierdzenie prośby, lobby i wejście do świata na dwóch komputerach.
