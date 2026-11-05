# Inspecto · android

Android-приложение информационной системы Inspecto — основной клиент для проведения осмотра на объекте: объекты и помещения, чек-листы, дефекты с фотографиями, черновики без сети.

Архитектура, контракты API и стандарты — в репозитории [`platform`](https://github.com/inspecto-system/platform): правила качества — `docs/CODE_QUALITY.md`, план задач — `docs/TASKS.md`.

## Технологии

Kotlin 2.4, Jetpack Compose (Material 3), Navigation Compose, Hilt, Retrofit + OkHttp + kotlinx.serialization; `minSdk` 26, `targetSdk` 37. Клиент API генерируется openapi-generator из копии контракта в `api/` (тег указан в `api/README.md`).

## Сборка и запуск

Нужны Android Studio (или Android SDK с платформой 37) и JDK 21 — Gradle запускается на Java 21 (`gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # установка на запущенный эмулятор или телефон
```

Адрес API по умолчанию — `https://10.0.2.2/api/v1/` (компьютер разработчика из эмулятора); другой адрес: `./gradlew assembleDebug -Pinspecto.apiBaseUrl=https://…/api/v1/`.

## Проверки качества

```bash
# форматирование
./gradlew ktlintFormat
# то же, что CI
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug
```

CI: `.github/workflows/ci.yml` → общий workflow `platform/.github/workflows/android.yml`, плюс Gitleaks, Dependency Review и CodeQL. Отчёты: `app/build/reports/`.

## Структура

```text
api/                 копия контракта OpenAPI и исключения генератора
config/detekt/       дополнения к правилам detekt
app/src/main/kotlin  код (dev.inspecto.android): core, feature, ui
app/src/test/kotlin  unit-тесты (JUnit 5) и UI-тесты Compose на JVM (Robolectric)
```
