# Zgłoszenie antywirusa

Początkowo zgłoszono ostrzeżenie przypisywane Windows Defenderowi. Użytkownik następnie wyjaśnił, że problem dotyczył Opery GX, podał brak wykryć w VirusTotal i uznał zgłoszenie za rozwiązane. Nie otrzymano linku do tego raportu i nie wykonano tu własnego skanu VirusTotal ani Defendera. Opis poniżej dokumentuje zawartość artefaktu i bibliotekę natywną; nie jest niezależnym audytem bezpieczeństwa.

Sprawdzony lokalny JAR 0.5.2 ma SHA-256:

`0ec41d6aed2a0c61c82248cfdb303037af8f53e79c0215efbc45fc856d1e437a`

Sam JAR nie zawiera EXE, DLL, skryptów PowerShell ani bibliotek SO. W trybie relay kod pobiera jednak właściwą natywną bibliotekę Netty QUIC z CDN e4mc i ładuje ją przez `System.load`. Na Windows jest to `netty_quiche_windows_x86_64_74.dll`. Przed ładowaniem wymaga przypiętego SHA-256:

`cca45c8f4c08525a4edf032b1c77dad9fa03ab99276f9df02a7a13126b600804`

Sprawdzenie sumy potwierdza zgodność z przypiętym plikiem, a nie nieszkodliwość kodu natywnego. Testy funkcjonalne moda nie są skanem Defendera ani audytem bezpieczeństwa. Biblioteki Windows nie przeskanowano tu Defenderem; środowisko nie jest Windows i nie pozwala narzędziom pobierać tej biblioteki ani uruchamiać sesji sieciowych.

Jeżeli pojawi się nowe konkretne wykrycie, do rozpoznania potrzebne są nazwa zagrożenia, nazwa pliku i moment alarmu: pobieranie JAR-a czy pierwsze tworzenie Party. Szczegóły Defendera są w [Historii ochrony aplikacji Zabezpieczenia Windows](https://support.microsoft.com/en-us/windows/security/windows-security/protection-history-in-the-windows-security-app). Ostrzeżenie przeglądarki i wykrycie konkretnego zagrożenia przez antywirusa to różne komunikaty.

GitHub udostępnia źródła i pliki do pobrania, lecz nie gwarantuje usunięcia alarmu. Przeniesienie tego samego JAR-a do innego serwisu nie zmienia jego bajtów. Kopia w `downloads` jest identyczna z lokalnym artefaktem; wydanie z CI jest osobnym buildem Gradle z własną sumą.

Potencjalne błędne wykrycie można przesłać do [Microsoft Security Intelligence](https://www.microsoft.com/en-us/wdsi/filesubmission). Nie przesłano żadnych plików do zewnętrznego skanera w tej sesji.
