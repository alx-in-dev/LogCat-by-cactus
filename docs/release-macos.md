# Выпуск macOS-сборки и плагина

Windows-установщик собирается и подписывается в GitHub Actions (см. [code-signing.md](code-signing.md)). Остальное делается локально на Mac.

## macOS: dmg

**dmg для выкладки собираем только через `scripts/make-dmg.sh`.** Образ, который выдаёт jpackage (`packageReleaseDmg`), содержит один `Logcat.app`: без ярлыка `Applications` и без оформления окна, перетащить приложение некуда. Скрипт собирает образ заново из готового `.app`: приложение слева, ярлык `Applications` справа, фон со стрелкой.

1. Собрать подписанное приложение на **JDK 17** (на JDK 21 падает proguard):
   ```bash
   cd LogCactus
   ./gradlew packageReleaseDmg -PmacSignIdentity="<Имя> (<TEAM>)"
   ```
   Нужен `.app` из `build/compose/binaries/main-release/app/Logcat.app`. Сам dmg из этой задачи выкладывать не нужно.
2. Собрать, подписать и нотаризовать образ:
   ```bash
   SIGN_IDENTITY="Developer ID Application: <Имя> (<TEAM>)" \
   NOTARY_PROFILE="<профиль notarytool>" \
   scripts/make-dmg.sh
   ```
   Профиль создаётся один раз: `xcrun notarytool store-credentials`. Без переменных скрипт соберёт неподписанный образ. Результат: `LogCactus/build/compose/binaries/main-release/dmg-custom/Logcat-<версия>.dmg`, в конце печатается SHA-256.
3. Проверить: `spctl -a -t open --context context:primary-signature -vv <dmg>` должен ответить `accepted, source=Notarized Developer ID`; образ смонтировать и убедиться, что внутри `Logcat.app` и `Applications`.
4. Добавить файл и сумму в релиз (`SHA256SUMS.txt`).

Скрипту нужны macOS, `python3` и доступ к PyPI: `dmgbuild` и `pillow` ставятся в `LogCactus/build/dmg-venv`. Раскладка окна лежит в `scripts/dmg/settings.py`, фон рисует `scripts/dmg/make_background.py`.

## Плагин для Android Studio

```bash
cd LogCactusPlugin
export JAVA_HOME=<JDK 21>
K=~/.logcactus-plugin-signing   # ключ и цепочка вне репозитория
export CERTIFICATE_CHAIN="$(cat $K/chain.crt)" PRIVATE_KEY="$(cat $K/private_encrypted.pem)" PRIVATE_KEY_PASSWORD="$(cat $K/password.txt)"
./gradlew verifyPlugin        # три IDE, около получаса, качает ~3 ГБ
./gradlew signPlugin
./gradlew verifyPluginSignature   # отдельным запуском
```

По умолчанию плагин собирается против самой старой поддерживаемой Студии (`minStudio` в `build.gradle.kts`, сейчас 2026.1.1, build 261): Compose и Jewel совместимы «вперёд», но не «назад». `-PstudioPath=<Android Studio.app>` подставляет локальную Студию, это нужно только для `runIde`. Список IDE для проверки — `pluginVerification` в том же файле. При смене `minStudio` или `sinceBuild` проверить плагин на старой и новой Студии.
