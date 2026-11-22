# Usługa kodów Minecraft Party

Kod do wdrożenia w Cloudflare Workers Free. Durable Object z backendem SQLite jest tworzony przez migrację w `wrangler.jsonc`. Nie jest potrzebny osobny VPS, domena, baza D1 ani token w modzie. Instrukcja po polsku: [docs/CLOUDFLARE.md](../docs/CLOUDFLARE.md).

`npm test` sprawdza politykę dostępu i wygasania. `npm run deploy` uruchamia przypięty Wrangler 4.166.0 i wdraża usługę po zalogowaniu do Cloudflare. Lokalne testy rzeczywistego runtime: `python3 test/runtime.py` (Node.js 24 i dostęp npm są wymagane). Samo uruchomienie testów nie wdraża niczego na konto.

Usługa przechowuje pełne zaproszenie mp3. Jej administrator i Cloudflare są zaufaną częścią systemu. Pokoje tworzone przez mod 0.7.0 żądają autoJoin: posiadacz sześciocyfrowego kodu dostaje zaproszenie bez akceptacji hosta. Starsze pokoje zachowują wcześniejszą politykę zatwierdzania. Każdy, kto zna lub zgadnie kod nowego pokoju, może dołączyć. Nick w żądaniu jest deklaracją klienta. Sześciocyfrowy kod jest obecnie samodzielnym uprawnieniem do Party, nie dowodem tożsamości. Logowanie do świata nadal weryfikuje Minecraft.

Protokołu v1 z opcjonalnym autoJoin używa `ShortCodeClient`; mod 0.7.0 wymaga obsługi tej opcji. Wszystkie operacje z kodami i sekretami korzystają z POST z JSON, odpowiedzi mają `Cache-Control: no-store`, brak CORS i brak własnego logowania treści. Bez adresu zaufanej usługi mod zachowuje dotychczasowe prywatne zaproszenia. Usługa nie jest systemem automatycznych zaproszeń przez listę znajomych Mojang.
