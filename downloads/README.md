# Gotowe prototypy moda

[Pobierz minecraft-party-0.6.0+26.2.jar z kodami Cloudflare](https://github.com/SigmaMiaek/minecraft-party/raw/refs/heads/main/downloads/minecraft-party-0.6.0%2B26.2.jar)

Wersja 0.6.0 jest kopią lokalnego artefaktu skompilowanego JDK 25 przeciw Minecraftowi 26.2 i zależnościom z Gradle cache. Lokalnie zaliczono 68 testów bez gniazd. Nie jest to artefakt z zakończonego builda Gradle Windows. Osobno GitHub Actions zaliczył pełny Gradle build/test Linux, w tym gniazda, i rzeczywisty test Worker + SQLite; test Windows w przebiegu [45021356399](https://github.com/SigmaMiaek/minecraft-party/actions/runs/45021356399) w momencie publikacji tego pliku nadal trwał.

Minecraft 26.2, Java 25, Fabric Loader 0.19.3+ i Fabric API 0.161.0+26.2. Skopiuj JAR do `mods` na obu komputerach; usuń wcześniejszy Minecraft Party i uruchom grę ponownie. [Ustaw adres usługi i zatwierdzaj prośby](../docs/CLOUDFLARE.md).

Sumy lokalnych artefaktów są w [SHA256SUMS](SHA256SUMS). Źródła są w tym repozytorium. Nie przeprowadzono testu kodów Party na dwóch działających klientach Minecraft ani własnego skanu Defendera.

[Starsza wersja 0.5.2 bez krótkich kodów](https://github.com/SigmaMiaek/minecraft-party/raw/refs/heads/main/downloads/minecraft-party-0.5.2%2B26.2.jar) pozostaje dostępna. Wydania w sekcji Releases są budowane oddzielnie przez Gradle po przejściu wymaganych zadań CI i mają własne sumy SHA256SUMS.
