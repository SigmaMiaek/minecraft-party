# Przekazywanie lidera — prototyp 0.7.0

Od 0.11.0 osobna funkcja **Przekaż świat…** najpierw kopiuje zamknięty zapis do wybranego odbiorcy, a dopiero po poprawnym imporcie korzysta z opisanej tu zmiany lidera. Zwykła gwiazdka lub opuszczenie lobby nadal nie kopiuje świata. [Instrukcja przekazania świata](WORLD_TRANSFER.md).

Wszyscy uczestnicy muszą zainstalować 0.7.0. Starsze wersje protokołu nie obsługują migracji.

## Ręcznie

W lobby lider klika **★** obok wybranego, połączonego uczestnika. Przycisk jest niedostępny podczas gry lub innej migracji. Wybrany klient przygotowuje własny szyfrowany listener i publiczny tunel e4mc. Dopiero po gotowości dotychczasowy lider kieruje uczestników na nowy adres. Poprzedni lider zostaje zwykłym uczestnikiem. Nie trzeba ponownie wpisywać kodów.

## Opuść Party

Jeżeli są inni połączeni członkowie, przycisk **Opuść Party** rozpoczyna przekazywanie pierwszemu z nich według kolejności lobby. Trzeba pozostawić grę uruchomioną do końca operacji. Jeśli następca nie może hostować, wybierana jest kolejna osoba. Gdy wszyscy kandydaci zawiodą, stary lider pozostaje w Party i dostaje błąd; system nie udaje udanej migracji. Jeśli nikt inny nie jest połączony, Party zostaje zamknięte zwyczajnie.

Lobby zachowuje identyfikator, nazwę, limit, historię czatu i blokady graczy. Przenoszone są indywidualne sekrety wznowienia; uczestnik nie może zastąpić innej osoby samym kodem zaproszenia. Nowy lider tworzy świeżą tożsamość TLS i nowy tunel e4mc. Wiadomości migracji akceptuje się tylko na uwierzytelnionym kanale; gotowość może zgłosić tylko wybrany następca. Nowi uczestnicy nie są przyjmowani w trakcie przygotowywania migracji.

Nowy lider otrzymuje **nowy sześciocyfrowy kod**. Osoby już połączone nie muszą go wpisywać. Nieobecni, którzy nie otrzymali komunikatu przeniesienia, muszą poprosić o nowy kod.

## Granice funkcji

- Nie ma migracji świata, ekwipunków ani plików zapisu. Udostępnianie poprzedniej sesji gry jest zamykane; pozostaje lobby, z którego nowy lider może wybrać grę.
- Nie ma automatycznych wyborów po awarii zasilania, zabiciu procesu ani zerwaniu całego połączenia przed przekazaniem danych. Zamykanie Minecrafta wymusza sprzątanie zamiast oczekiwania na migrację. To obsługa kontrolowanego wyjścia z Party, a nie konsensus odporny na awarie.
- Następca musi móc otworzyć e4mc; pierwsze użycie może pobrać bibliotekę natywną. Termin przygotowania wynosi około 90 s. Stary lider pozostaje serwerem lobby do rozesłania przełączenia; po zatwierdzeniu nie można niezawodnie cofnąć migracji, jeżeli nowy host nagle zniknie.
- Osoba grająca w niezależnym świecie lub na niezależnym serwerze nie jest automatycznie wyrzucana z tej gry w celu przejęcia roli. Taki kandydat odmawia przygotowania. Sesja gry pochodząca z Party może zostać zamknięta podczas przenoszenia.
- W trakcie przełączenia UI może krótko pokazywać ponowne łączenie i poprzedni stan. Tokeny, odciski certyfikatów i adresy nie trafiają do logów ani opisów obiektów migracji.

Test modelu i kodowania nie dowodzi działania dwóch klientów przez internet. Test TLS loopback jest osobno oznaczony `network`. Rzeczywista rozgrywka i migracja e4mc między dwoma komputerami pozostają do wykonania.
