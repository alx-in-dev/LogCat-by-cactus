# Подпись Windows-установщика (SignPath Foundation)

`.msi` собирается в GitHub Actions ([`windows-installer.yml`](../.github/workflows/windows-installer.yml)) и подписывается
через [SignPath](https://signpath.io) бесплатным сертификатом [SignPath Foundation](https://signpath.org) для открытых проектов.
Условия программы: <https://signpath.org/terms>.

## Однократная настройка

1. **Заявка.** <https://signpath.org/apply> — для проекта `LogCat by cactus` (репозиторий `alx-in-dev/LogCat-by-cactus`).
   Включите MFA на GitHub и в SignPath — это условие программы. На главной странице проекта (README) уже есть раздел
   «Code signing policy» с требуемой формулировкой.
2. **После одобрения** в SignPath: создать проект со slug `logcat-by-cactus` и политику подписи со slug `release-signing`
   (подпись с ручным одобрением каждого запроса), подключить Trusted Build System «GitHub.com» к проекту.
3. **Конфигурация артефакта** (Artifact configuration, по умолчанию) — подписываем только `.msi`:

   ```xml
   <artifact-configuration xmlns="http://signpath.io/artifact-configuration/v1">
     <zip-file>
       <msi-file path="Logcat-*.msi">
         <authenticode-sign/>
       </msi-file>
     </zip-file>
   </artifact-configuration>
   ```

   Схема взята из памяти, перед использованием сверьте её с документацией SignPath (<https://docs.signpath.io>): редактор
   конфигурации в SignPath проверяет XML. Если установщик внутри содержит `.exe`/`.dll` собственной сборки, их можно
   подписать там же; сторонние бинарники (`adb`, `aapt2`) подписывать нельзя.
4. **GitHub → Settings → Secrets and variables → Actions:**
   - secret `SIGNPATH_API_TOKEN` — токен пользователя-submitter из SignPath;
   - variable `SIGNPATH_ORGANIZATION_ID` — ID организации из SignPath;
   - при других slug — variables `SIGNPATH_PROJECT_SLUG`, `SIGNPATH_SIGNING_POLICY_SLUG`.

Пока переменная и секрет не заданы, workflow собирает `.msi` без подписи.

## Выпуск

Поставить тег `vX.Y.Z` и запушить — workflow соберёт установщик, отправит запрос на подпись, дождётся одобрения в SignPath
(письмо/кнопка Approve) и приложит подписанный `.msi` и обновлённые `SHA256SUMS.txt` к GitHub Release этого тега.
Ручной запуск (Actions → Windows installer → Run workflow) с `publish=false` только проверяет сборку и подпись.
