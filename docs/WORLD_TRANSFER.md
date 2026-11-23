# Przekazanie świata wybranemu graczowi

Od prototypu 0.11.0 host może przekazać kopię bieżącego świata singleplayer jednej wybranej osobie w Party. Po imporcie ta osoba przejmuje Party, otwiera otrzymany świat, a pozostali korzystają z automatycznego dołączania. Wszyscy potrzebują Minecraft Party 0.11.0 dla Minecraft 26.2. Watch Party, przeglądarka i zależność Rinku zostały usunięte z moda.

## Obsługa

1. Host i odbiorca są w tym samym Party; host udostępnia uruchomiony świat singleplayer.
2. Host wybiera **ESC → Party → Przekaż świat…**, wskazuje osobę i naciska **Zapisz i przekaż świat**. Opcja **Opuść potem Party** jest domyślnie włączona; można ją wyłączyć, by samemu dołączyć do kopii u nowego hosta.
3. Wybrany odbiorca akceptuje **Przyjmij i hostuj**. Do tego czasu świat pozostaje uruchomiony. Odrzucenie nie zamyka świata.
4. Mod wycofuje udostępnianie i zamyka zintegrowany serwer standardową drogą Minecrafta. Gra ma przerwę na końcowy zapis. Po zatrzymaniu serwera mod uzyskuje wyłączną blokadę katalogu świata i przygotowuje archiwum. Nie pakuje zapisu podczas działania serwera.
5. Ekran pokazuje zapis, pakowanie, liczbę przesłanych MiB, sprawdzanie/import i zmianę hosta. Dane trafiają wyłącznie do wybranego odbiorcy. Pozostali pozostają w lobby, bez pobierania całego świata.
6. Odbiorca sprawdza SHA-256, rozpakowuje nową kopię, ustawia właściciela zapisu i zgłasza gotowość. Dopiero wtedy zaczyna się dotychczasowa procedura przekazania lidera Party. Odbiorca uruchamia otrzymany świat standardowym `WorldOpenFlows.openWorld`, a pozostali automatycznie łączą się z nowym hostem.

Dotychczasowy host musi pozostać online do zakończenia przesyłania i przekazania Party. Nie jest to zmiana hosta bez przerwy. Czas zależy od wielkości świata, dysków, połączenia i dostępności relaya.

## Ochrona zapisu i danych graczy

- Oryginał pozostaje u poprzedniego hosta i nie jest modyfikowany przez archiwizację. Błąd ani anulowanie nie usuwa tego zapisu. Po przerwaniu przy już zamkniętym świecie dostępny jest przycisk **Otwórz oryginalny świat**.
- U odbiorcy tworzony jest odrębny katalog `saves/Party-<identyfikator>`. Nie następuje scalanie ani nadpisywanie istniejących światów. Kopia jest przenoszona do widocznego katalogu dopiero po poprawnym rozpakowaniu i dostosowaniu właściciela. Gotowa kopia pozostaje dostępna w Singleplayer także wtedy, gdy późniejsza zmiana lidera lub otwarcie świata zawiedzie.
- Kopiowane są całe regularne pliki zapisu, w tym wymiary, regiony, encje, dane graczy, postępy, statystyki i datapacki. Pomijany jest `session.lock`. Dowiązania i pliki specjalne powodują odrzucenie, zamiast odczytu danych spoza świata.
- Minecraft 26.2 przechowuje wskaźnik lokalnego właściciela w `Data.singleplayer_uuid`, a pliki graczy w ścieżce wskazanej przez `LevelResource.PLAYER_DATA_DIR` (`players/data`). W kopii zmienia się ten wskaźnik, z zachowaniem danych graczy pod ich UUID. Starsze osadzone `Data.Player` są obsługiwane przy braku osobnego pliku poprzedniego właściciela. Kopia nie daje nowemu hostowi ekwipunku poprzedniego. Dostosowywany jest również `level.dat_old`.
- Brak wymaganych danych poprzedniego gracza zatrzymuje import zamiast świadomego resetu jego postępów. Nie są wyłączane uwierzytelnianie Minecrafta, ostrzeżenia wersji ani kontrolki błędów datapacków.

Kopia obejmuje cały zapis — odbiorca otrzymuje też dane pozostałych graczy znajdujące się w tym świecie. Przekazuj go osobie, której chcesz oddać taki zapis. Po kontynuacji najnowszą wersję posiada nowy host; stary oryginał nie synchronizuje się automatycznie z dalszą grą.

## Połączenie, limity i błędy

Transfer używa osobnego strumienia TLS 1.3, z istniejącym pinningiem i indywidualnym poświadczeniem wznowienia uczestnika. Serwer sprawdza docelowy UUID i identyfikator konkretnej zaakceptowanej operacji. Sam kod Party ani uprawnienia innego uczestnika nie pozwalają pobrać archiwum. Strumień używa tego samego transportu bezpośredniego lub relaya e4mc; nie wymaga dodatkowego portu, konta, hostingu plików ani programu. Cloudflare przechowuje nadal tylko katalog kodów, bez świata i jego danych.

Chwilowe przerwanie transmisji pozwala wznowić plik od zapisanej pozycji przy odzyskaniu tej samej sesji. Ponawiane są też istotne sygnały sterujące. Ręczne anulowanie zamyka strumień. Po zmianie lidera rozpoczyna się nowe połączenie do nowego hosta.

Limity prototypu: archiwum do 8 GiB, dane po rozpakowaniu do 32 GiB, 200 tys. wpisów, ograniczone długości ścieżek oraz 32 MiB na odczyt pojedynczego NBT właściciela. Odbiorca musi mieć miejsce na archiwum, rozpakowane dane i zapas 256 MiB. Niebezpieczne ścieżki ZIP, duplikaty, kolizje wielkości liter, nazwy niedozwolone w Windows i przekroczenia zadeklarowanego rozmiaru są odrzucane. Transfer ma limit dwóch godzin, odzyskiwanie zerwanego połączenia około 110 sekund, a oferta wymaga odpowiedzi w 90 sekund.

Wymagane są zgodne wersje Minecrafta, modów świata i datapacków. Jeśli vanilla poprosi o decyzję dotyczącą otwarcia świata, trzeba rozwiązać ten komunikat. W razie anulowania ładowania dostępne jest **Otwórz otrzymany świat**. Utrata hosta przed ukończeniem nie przeniesie brakujących danych; odbiorca nie dostaje częściowego zapisu w głównej liście światów. Po awarii procesu mogą pozostać pliki tymczasowe w `minecraft-party-transfers` lub `saves/.minecraft-party-imports`; można je sprzątnąć przy wyłączonej grze. Nie usuwaj katalogu gotowego świata `Party-…`.

## Weryfikacja

Testy obejmują rzeczywiste pliki i NBT, zachowanie obu zestawów danych graczy, niedestrukcyjny import, limity i ścieżki ZIP. Testy sieciowe w GitHub Actions używają rzeczywistego TLS na loopback: zgoda jednego odbiorcy, odrzucenie innego uczestnika, anulowanie/wznowienie pliku, SHA-256 i odblokowanie przekazania lidera. Istniejące testy migracji lidera i tunelu świata pozostają wymagane.

**Nie wykonano pełnego testu z dwoma uruchomionymi klientami Minecraft, rzeczywistym światem, internetem ani różnymi NAT.** Kompilacja, testy NBT i transportu nie dowodzą jeszcze, że cały przepływ menu → zapis → nowy host → gra przeszedł na dwóch komputerach. Trzeba to sprawdzić na kopii świata, szczególnie przy dodatkowych modach zapisujących dane we własnych formatach.
