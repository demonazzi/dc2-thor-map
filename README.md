# DC2 Карта — второй экран для AYN Thor

Живая карта подземелья Dark Cloud 2 (US, SCUS-97213) на нижнем экране Тора. Читает память игры из ARMSX2 по PINE (127.0.0.1:28011) и рисует `map.html`.

## Сборка
GitHub Actions собирает APK при каждом push: вкладка **Actions** → последняя сборка → **Artifacts** → `dc2-map-N` (zip с APK внутри).
Подпись — фиксированный ключ `dc2map.keystore` из репозитория, поэтому новые версии ставятся поверх старых.

## Использование
1. В ARMSX2 включи PINE (порт 28011), запусти игру.
2. Запусти «DC2 Карта» на нижнем экране.
3. Опции карты — кнопка «опции» в правом верхнем углу.

Порт можно поменять: `adb shell am start -n ru.misha.dc2map/.MainActivity --ei pine_port 28012`.

## Устройство
- `app/src/main/assets/map.html` + `img/` — страница карты (та же, что на ПК с `dc2_map_server.py`).
- `GameReader.kt` — чтение памяти игры (порт `dc2_map_server.py` v1), `Pine.kt` — клиент PINE.
- `MainActivity.kt` — WebView, мост `window.DC2.state()`, опрос эмулятора ~10 раз в секунду в фоне.
