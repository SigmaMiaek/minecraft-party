# Usługa kodów Minecraft Party

Od 0.10.0 katalog przyjmuje również `protocol: 6` (Watch Party, limit 2–10). `/health` zwraca `partyProtocols: [4, 5, 6]`. Dane filmu i stan odtwarzania nie są przesyłane do Workera — idą kanałem TLS Party.

W 0.9.0 lista przyjmuje `protocol: 5` i limity 2–10 (łącznie z hostem). Brak parametru `protocol` zachowuje listę MPT4 dla 0.8.0 i limit 2–8; oba protokoły widzą wyłącznie zgodne wpisy. Odpowiedź listy zwraca wybrany protokół. `/health` ogłasza `partyProtocols: [4, 5]` i `maxPlayers: 10`. Wdrożenie aktualizuje ten sam Worker i tę samą bazę bez zmiany adresu w modzie.

Od 0.8.0 `POST /v1/list` udostępnia wyłącznie lobby jawnie utworzone jako publiczne. Nowe i starsze pokoje są domyślnie prywatne. Publiczny wpis zawiera kod dołączenia, nazwę, deklarowanego hosta, liczbę miejsc i status gry; bez zaproszenia mp3, właścicielskiego tokenu i adresów połączeń. Tylko właściciel odnawia wpis i zmienia metadane. Lista ma stronicowanie oraz limity, a wpisy wygasają po 120 sekundach bez heartbeat. `/health` sygnalizuje `publicLobbies: true`. Nie jest wymagana nowa migracja bazy: dotychczasowe rekordy pozostają prywatne.

Kod do wdrożenia w Cloudflare Workers Free. Durable Object z backendem SQLite jest tworzony przez migrację w `wrangler.jsonc`. Nie jest potrzebny osobny VPS, domena, baza D1 ani token w modzie. Instrukcja po polsku: [docs/CLOUDFLARE.md](../docs/CLOUDFLARE.md).

Ten sam Worker publikuje teraz stronę moda z `../website/dist`. Katalog `cloudflare` i dotychczasowe komendy wdrożenia pozostają poprawne. Główny adres `/` wyświetla stronę, a `/health` i `/v1/*` mają pierwszeństwo przed zasobami statycznymi i nadal obsługują usługę kodów. Nie zmieniaj adresu usługi w modzie. [Źródła i podgląd strony](../website/README.md).

`npm test` sprawdza politykę dostępu i wygasania. `npm run deploy` uruchamia przypięty Wrangler 4.166.0 i wdraża usługę po zalogowaniu do Cloudflare. Lokalne testy rzeczywistego runtime: `python3 test/runtime.py` (Node.js 24 i dostęp npm są wymagane). Samo uruchomienie testów nie wdraża niczego na konto.

Usługa przechowuje pełne zaproszenie mp3. Jej administrator i Cloudflare są zaufaną częścią systemu. Pokoje tworzone przez mod 0.7.0 żądają autoJoin: posiadacz sześciocyfrowego kodu dostaje zaproszenie bez akceptacji hosta. Starsze pokoje zachowują wcześniejszą politykę zatwierdzania. Każdy, kto zna lub zgadnie kod nowego pokoju, może dołączyć. Nick w żądaniu jest deklaracją klienta. Sześciocyfrowy kod jest obecnie samodzielnym uprawnieniem do Party, nie dowodem tożsamości. Logowanie do świata nadal weryfikuje Minecraft.

Protokołu v1 z opcjonalnym autoJoin używa `ShortCodeClient`; mod 0.7.0 wymaga obsługi tej opcji. Wszystkie operacje z kodami i sekretami korzystają z POST z JSON, odpowiedzi mają `Cache-Control: no-store`, brak CORS i brak własnego logowania treści. Bez adresu zaufanej usługi mod zachowuje dotychczasowe prywatne zaproszenia. Usługa nie jest systemem automatycznych zaproszeń przez listę znajomych Mojang.
