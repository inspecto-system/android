# Контракт API

Копия `core.yaml`, `media.yaml` и `common.yaml` из `inspecto-system/platform` с тега **`api-v0.5.0`**. Клиенты Retrofit генерируются при сборке (ADR-014): core-service — `./gradlew openApiGenerate` (пакет `dev.inspecto.android.api`), media-service — `./gradlew openApiGenerateMedia` (пакет `dev.inspecto.android.media.api`, только публичные операции с фото).

Обновление: заменить файлы версией с нового тега контракта и указать тег здесь — отдельным PR после PR в `platform`.
