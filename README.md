# Weather App (Android)

Простий Android-додаток погоди з геолокацією.

## Що реалізовано

- Запит дозволу на геолокацію при вході в застосунок.
- Отримання поточних координат користувача.
- Запит погоди по координатах через безкоштовний API **Open-Meteo** (без API ключа).
- Кнопка `Оновити` для повторного запиту геолокації та погоди.
- Автооновлення координат і погоди при кожному поверненні в застосунок (`onResume`).
- Динамічний фон і велика іконка стану погоди залежно від `weather_code`.

## API

- Endpoint: `https://api.open-meteo.com/v1/forecast`
- Використовуються поля: `temperature_2m`, `weather_code`, `wind_speed_10m`

## Запуск

1. Відкрий папку проєкту в Android Studio.
2. Дочекайся Gradle Sync (Android Studio підвантажить потрібний Gradle).
3. Запусти на емуляторі або фізичному пристрої.
4. Дай дозвіл на геолокацію.

## Основні файли

- `app/src/main/java/com/example/weatherapp/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
