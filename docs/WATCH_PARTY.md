# Watch Party — YouTube na ekranie w świecie

Pierwszy prototyp w 0.10.0 używa oficjalnego YouTube IFrame API i opcjonalnego moda [Rinku](https://modrinth.com/mod/rinku). Ekran jest powierzchnią renderowaną przez klientów Party w tych samych współrzędnych świata, z testowaniem głębokości względem bloków. Nie jest zapisanym blokiem ani encją i nie wymaga dodatkowego moda na dedykowanym serwerze. Jeden host może udostępniać jeden ekran na sesję świata.

## Instalacja i oglądanie

1. Wszyscy instalują Minecraft Party **0.10.1+26.2**. Osoby oglądające dodatkowo instalują **Rinku 3.0.4 lub nowsze z gałęzi 3.x, Fabric, Minecraft 26.2** z oficjalnej [listy wersji](https://modrinth.com/mod/rinku/versions?g=26.2&l=fabric). W menu Watch Party jest przycisk Pobierz Rinku. Rinku przy uruchomieniu przygotowuje swój natywny pakiet Chromium; pobieranie i konfigurację obsługuje ten mod. Bez Rinku można nadal używać zwykłych funkcji Party.
2. Utwórz Party i wejdźcie razem do świata hosta. Następnie **ESC → Party → Watch Party…**.
3. Host wkleja link do YouTube albo 11-znakowe ID filmu. Wybiera rozmiar i naciska **Ustaw ekran**. Ekran pojawia się sześć bloków przed hostem, pionowo, w proporcjach 16:9. Film zaczyna od pauzy. Ponowne ustawienie zastępuje ekran i rozpoczyna nową sesję oglądania.
4. Pozostali wybierają **Dołącz do oglądania**. Każdy klika raz podgląd filmu, aby zezwolić przeglądarce na odtwarzanie i dźwięk. Host po ustawieniu ekranu dołącza lokalnie automatycznie, ale również musi wykonać to kliknięcie.
5. Host używa przycisków Odtwórz / Pauza, ±10s lub wpisuje czas i naciska Przewiń. Wstecz przywraca Party, a następnie grę; odtwarzacz i ekran pozostają aktywne po zamknięciu menu. Pozostałe kontrolki odtwarzacza YouTube, np. napisy i jakość, są lokalne. Ręczna pauza w samym odtwarzaczu zostanie zastąpiona stanem hosta.
6. Głośność ustawia się osobno u każdego gracza. Respektuje główną głośność Minecrafta, cichnie od 16 do 48 bloków i milknie w innym wymiarze. Ekran renderuje się w odległości do 64 bloków. **Nie oglądaj** zamyka tylko Twój odtwarzacz; **Usuń ekran** u hosta kończy sesję wszystkim.

## Synchronizacja i cykl życia

Film każdy pobiera bezpośrednio z YouTube. Przez szyfrowany kanał TLS Party przesyłane są wyłącznie ID filmu, współrzędne, czas i stan odtwarzania hosta. Gość nie ma komend zmiany wspólnego odtwarzania. Nowy widz otrzymuje aktualny stan. Po utracie połączenia odtwarzacz zostaje zamknięty; po odzyskaniu tej samej sesji może zostać ponownie otwarty, z koniecznością ponownego kliknięcia podglądu. Wyjście ze świata albo Party sprząta lokalny odtwarzacz i serwer strony. Następny świat nie dziedziczy ekranu.

Pozycja filmu korzysta z zegara monotonicznego. Host wysyła zmiany od razu, a stan również przy okresowych aktualizacjach lobby. Klient koryguje rozbieżność powyżej 1,5 sekundy, nie częściej niż co trzy sekundy. Nie jest to synchronizacja klatka w klatkę: opóźnienie sieci, buforowanie i różne reklamy mogą rozsunąć widzów. Na początek najlepiej zaczekać na gotowość obu odtwarzaczy i dopiero wtedy włączyć film.

## Źródła i ograniczenia

- Obsługiwane są identyfikatory i linki HTTPS `youtube.com/watch`, `youtu.be`, `shorts`, `embed` i `live`. Nie są zachowywane parametry konta, playlisty ani startu z linku. Czas ustawia host kontrolkami. Dla transmisji na żywo przewijanie może być niedostępne po stronie YouTube.
- Wiek, region, prywatność filmu, wymagane logowanie i zakaz osadzania nadal obowiązują. Kod nie pobiera strumieni poza oficjalnym odtwarzaczem, nie pomija reklam i nie przenosi sesji logowania między graczami.
- **Netflix i Prime Video nie są obsługiwane w tym wydaniu.** Użytkownik wybrał najpierw ekran YouTube. Ich DRM i wspierane środowiska wymagają osobnego rozwiązania; nie ma atrapy tych usług.
- Zmiana lidera nie przenosi filmu wraz ze światem; obecna migracja lidera działa w lobby po zamknięciu świata.
- Nie wykonywano tu oglądania w rzeczywistym Minecraft, renderowania na GPU, testu natywnego dźwięku ani próby dwóch komputerów. Kompilacja i testy transportu/sterowania nie zastępują tych prób. Zgodność z Lunar/Dawn, shaderami i innymi modami przeglądarki wymaga testu w grze.

## Prywatność i zależności

Oglądanie jest dobrowolne, osobno dla każdej nowej sesji filmu. YouTube oraz infrastruktura dostarczająca film widzą połączenia każdego oglądającego; konta i hasła nie trafiają do Party ani do innych uczestników. Lokalna strona odtwarzacza wiąże się wyłącznie z `127.0.0.1`, używa losowej ścieżki dostępu, kontroli nagłówka Host, CSP oraz ograniczonej ramki. Nie udostępnia zdalnego API sterowania, plików ani haseł i nie zapisuje adresu z sekretem w logach.

Rinku jest osobnym projektem na LGPL-2.1-or-later: [źródła i licencja](https://github.com/Keksuccino/Rinku), autor Keksuccino, na podstawie MCEF autorów ds58 i CinemaMod. Party używa jego publicznego API przez opcjonalny adapter, nie dołącza jego kodu ani JAR-a i nie zależy od Essential Mod. Adapter sprawdzany jest w CI przeciwko rzeczywistemu opublikowanemu JAR-owi Rinku. Odtwarzacz używa [oficjalnego API YouTube](https://developers.google.com/youtube/iframe_api_reference).
