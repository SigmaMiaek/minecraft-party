# Stan etapów

| Etap | Stan tego wydania | Warunek zamknięcia |
| --- | --- | --- |
| 1. Fabric, Gradle, ekran | Źródła i wrapper gotowe; kompilacja JDK udana; Gradle blokowany przez sandbox | Uruchomienie Gradle i klienta poza sandboxem |
| 2. Lokalny model lobby | Zaimplementowany, testy modelu zaliczone | Również ręczne sprawdzenie UI |
| 3. Dwie instancje | Rzeczywisty transport TLS i testy gniazd napisane; wykonanie blokowane | Zaliczony loopback i dwie instancje gry |
| 4. Internet i zaproszenia | Dodany publiczny relay e4mc z mp3; bezpośredni IPv4/UPnP pozostaje opcją | Test publicznego relaya, natywnej biblioteki i dwóch sieci; ICE pozostaje osobnym rozszerzeniem |
| 5. Most serwera | Zaimplementowany dla 26.2, kompiluje się | Test działania accessorów, loginu i rozgrywki |
| 6. Auto join / lobby | Maszyna sesji i cleanup zaimplementowane | Dwa światy po kolei, późny gość, utrata połączenia |
| 7. UI / bezpieczeństwo / języki | Ekran PL/EN, walidacja, pinning, limity, revoke i kick | Testy wizualne i przegląd bezpieczeństwa w uruchomionej grze |
| 8. Macierz testów | 65 testów bez gniazd zaliczone; 4 gniazdowe zablokowane w próbie 0.5.0 | Dwaj zalogowani klienci Windows/Linux, LAN, internet i różne NAT |

## Kolejność dalszych prac

1. Sprawdzić przygotowanie natywnej biblioteki i publiczny relay e4mc na hoście Starlink oraz gościu w hotspocie. Równolegle uruchomić obecne testy gniazd w zwykłym środowisku. Naprawić wykryte błędy przed rozszerzaniem transportu.
2. Wykonać scenariusze dwóch legalnie zalogowanych klientów z TESTING.md, szczególnie most i cykl dwóch światów.
3. Pobrać narzędzia dla aktualnej stabilnej wersji 26.3; przeportować i ponownie przetestować mixiny, protokół logowania, Screen API i ConnectScreen.
4. Porównać mały eksperyment ice4j z webrtc-java DataChannel: narzut dystrybucji, stabilność JNI, licencje zależności, opóźnienie i pamięć przy przesyłaniu chunków. Nie wybierać jedynie na podstawie nazwy WebRTC.
5. Dodać ręczne, prywatne offer/answer per uczestnik. Uwierzytelnić wyzwania i klucze, obsłużyć timeout i odrzucenie odpowiedzi do innego lobby. Żaden własny serwer sygnalizacyjny nie jest wymagany.
6. Dodać ICE/STUN i transport z kontrolą przeciążenia, DTLS oraz niezawodnym strumieniem. Opcjonalne UPnP/PCP/NAT-PMP muszą sprzątać mapowania i być wyraźnie opisane w preferencjach.
7. Przetestować pełną macierz NAT. Bez TURN zgłaszać nieosiągalność tam, gdzie bezpośrednie połączenie nie powstaje. Nie prezentować nieudanej próby jako działającego lobby.


## Zaimplementowane w 0.2.0

Vanilla UI z dirtem i układem ze szkiców, głowy graczy, czat w lobby i boczny panel menu głównego. Host może udostępnić wybrany zewnętrzny serwer multiplayer; goście otrzymują tę samą domenę/port i uruchamiają standardowe logowanie. Testy modelu i protokołu są zaliczone; wizualne i dwa klienci nadal do sprawdzenia. Przechodzenie NAT pozostaje osobnym, niewykonanym etapem.


## Zaimplementowane w 0.3.0

Lista bez czatu pod ESC, wybór świata/serwera przez zwykłe menu MC, przyciski OP+/OP− na czas świata i odrębny przełącznik /tp. 30 testów bez gniazd zaliczonych; mixiny uprawnień i scenariusze komend wymagają testów w uruchomionym kliencie.


## Zaimplementowane w 0.4.0

Zachowane długie zaproszenia, bez sześciocyfrowych kodów, relaya i usługi hostingowej. Tryb Internet wybiera publiczny interfejs IPv4 lub próbuje czasowego mapowania UPnP; jawny LAN pozostaje opcją. 41 testów bez gniazd zaliczonych. Dostępność z drugiej sieci, działanie rzeczywistego routera oraz sprzątanie/odnowienie mapowania wymagają testu terenowego. ICE, IPv6, PCP/NAT-PMP nie są ukończone.

## Zaimplementowane w 0.4.1

Usunięty błędny wybór znanych adapterów VPN jako kandydatów bezpośredniego internetu i interfejsów UPnP. Etapy TCP, TLS i lobby mają osobne błędy; host zlicza przyjęcia/odmowy, a ekran Sieć… kopiuje raport bez adresów i sekretów. 48 testów bez gniazd zaliczonych. Poprawka wymaga nowego zaproszenia; skuteczność połączenia w zgłoszonej parze sieci nadal wymaga ponownego testu.

## Zaimplementowane w 0.5.0

Po zgodzie użytkownika dodany publiczny relay e4mc zamiast wymagania własnego hostingu. Party używa wychodzącego QUIC oraz wewnętrznego TLS; lobby i świat korzystają z tej samej ekspozycji. Nowe zaproszenia mp3, tryb relay domyślny, brak wymogu instalacji całego e4mc. Kod biblioteki QUIC jest wbudowany, natywna biblioteka pobierana i sprawdzana według przypiętego SHA-256. 60 testów bez gniazd zaliczonych. Nie wykonano sesji z relayem, ładowania JNI, dwóch klientów ani testu Starlink/hotspot.

## Poprawione w 0.5.1

Przycisk Dołącz akceptuje mp3 i mp2 przez wspólne rozpoznawanie z parserem, także z brzegowymi białymi znakami. Poprawione wskazówki PL/EN i natychmiastowe odświeżanie przycisku po wklejeniu. Zaproszenia pozostają zgodne z 0.5.0; 61 testów bez gniazd zaliczonych. Ręczny test UI i rzeczywiste dołączenie nadal wymagają uruchomionej gry.

## Poprawione w 0.5.2

Odtworzony i naprawiony brak wznowienia manualnego odczytu po prefiksie bez pierwszych bajtów TLS. Autojoin dopuszcza zwykłe i niestandardowe menu klienta, chroniąc aktywne światy/połączenia i wybór/ładowanie świata. Dodane etapy mostu i autojoin oraz liczniki relaya. 65 testów bez gniazd zaliczonych. Użytkownik zgłosił połączenie Party; brak potwierdzenia pełnej rozgrywki i testu Lunar przez narzędzia.
