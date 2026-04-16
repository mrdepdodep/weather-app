# Weather App (Android)

Простий Android-додаток погоди з ручним вибором міста.

## Що реалізовано

- Ввід міста та вибір одиниць температури (`°C` або `°F`) у налаштуваннях.
- Кнопка-шестерня в інтерфейсі, щоб будь-коли змінити місто та одиниці.
- Віджет погоди на головний екран (home screen widget).
- Щоденні сповіщення про погоду в обраний час.
- Збереження налаштувань міста/одиниць локально.
- Запит координат міста через **Open-Meteo Geocoding API**.
- Запит погоди по координатах через безкоштовний API **Open-Meteo** (без API ключа).
- Кнопка `Оновити` для повторного запиту погоди за поточними налаштуваннями.
- Динамічний фон і велика іконка стану погоди залежно від `weather_code`.

## API

- Geocoding endpoint: `https://geocoding-api.open-meteo.com/v1/search`
- Weather endpoint: `https://api.open-meteo.com/v1/forecast`
- Використовуються поля: `temperature_2m`, `weather_code`, `wind_speed_10m`

## Запуск

1. Відкрий папку проєкту в Android Studio.
2. Дочекайся Gradle Sync (Android Studio підвантажить потрібний Gradle).
3. Запусти на емуляторі або фізичному пристрої.
4. Введи місто та обери одиниці температури при першому запуску.

## Build через GitHub Actions

- Workflow: `.github/workflows/android-build.yml`
- Запускається на `push` і `pull_request` для `main/master`, а також вручну (`workflow_dispatch`).
- Кроки CI: встановлення JDK 17 + Android SDK, запуск unit-тестів, збірка `:app:assembleDebug`.
- Готовий APK зберігається як artifact `app-debug-apk`.

## Основні файли

- `app/src/main/java/com/example/weatherapp/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
