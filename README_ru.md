# Palleria

[English](README.md) · [日本語](README_ja.md) · [한국어](README_ko.md) · [Русский](README_ru.md)

<p align="center">
  <img
    src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/icon.png"
    alt="Иконка Palleria"
    width="160"
  />
</p>

<h3 align="center">
  Современный высокопроизводительный клиент Pixiv с открытым исходным кодом для Android
</h3>

<p align="center">
  Просматривайте иллюстрации, мангу и романы через сверхбыстрый и плавный интерфейс, созданный на Jetpack Compose, Miuix UI и нативном Rust.
</p>

<p align="center">

[![Лицензия](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Rust](https://img.shields.io/badge/Rust-Native%20Core-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Miuix KMP](https://img.shields.io/badge/Design-Miuix%20KMP-FF6900.svg?style=flat-square)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![F-Droid](https://img.shields.io/badge/F--Droid-Repository-1976D2.svg?style=flat-square&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo/)
[![Релиз на GitHub](https://img.shields.io/github/v/release/yunfie-twitter/Palleria?style=flat-square&logo=github&label=Release)](https://github.com/yunfie-twitter/Palleria/releases)
[![Загрузки на GitHub](https://img.shields.io/github/downloads/yunfie-twitter/Palleria/total?style=flat-square&logo=github&label=Downloads)](https://github.com/yunfie-twitter/Palleria/releases)
[![Звёзды на GitHub](https://img.shields.io/github/stars/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/stargazers)
[![Issues на GitHub](https://img.shields.io/github/issues/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/issues)
[![Диаграмма архитектуры](https://gitdiagram.com/diagram-badge.svg)](https://gitdiagram.com/yunfie-twitter/palleria?utm_source=readme&utm_medium=badge)
</p>

---

## О проекте

**Palleria** — бесплатное Android-приложение с открытым исходным кодом, ставящее во главу угла конфиденциальность и созданное для того, чтобы обеспечить лучший опыт просмотра Pixiv на мобильных устройствах.

Palleria построена с нуля на Jetpack Compose, дизайн-языке Miuix и нативном бэкенде на Rust через UniFFI. Она сочетает производительность настольного уровня, низкое энергопотребление, насыщенные тактильные микровзаимодействия и сквозное (end-to-end) шифрование синхронизации.

> [!IMPORTANT]
> Palleria — неофициальный клиент Pixiv и не связан с Pixiv Inc., не одобрен ею и не аффилирован с ней.

---

## Ключевые особенности

*  **Высокопроизводительное ядро на Rust**: нативный движок UniFFI для ускоренного декодирования Ugoira, конвертации в APNG/WebP/MP4 и обработки данных без накладных расходов.
*  **Современный UI: Miuix и Material You**: плавные анимации, темы AMOLED и динамические темы под акценты произведений, а также настраиваемые адаптивные сетки с несколькими столбцами.
*  **Незаметные микро-UX-инновации**:
  * **Предварительный DNS и прогрев сокетов**: заранее резолвит DNS и создаёт пулы сокетов TLS 1.3, пока отображается сплэш-экран, — для мгновенной загрузки первого изображения.
  * **Дельта-синхронизация и ETag**: дифференциальная синхронизация ленты подписок, которая никогда не скачивает повторно уже известные работы и заранее прогревает изображения в высоком разрешении в фоне.
  * **Упреждающая выборка по инерции**: предсказывает точку остановки инерционного прокручивания (fling), чтобы заранее загрузить работы в месте остановки.
  * **Незаметное возобновление загрузок**: продолжает прерванные загрузки по смещениям байтов HTTP 206 `Range`, не начиная с нуля.
  * **Адаптивная предзагрузка манги**: определяет привычки направления чтения (RTL / LTR) за два перелистывания и автоматически подстраивает очередь предзагрузки.
  * **Вежливое затухание звука**: 100-мс логарифмическое плавное снижение громкости при отключении наушников — чтобы уберечь уши и не устроить утечку звука в общественном месте.
  * **Тактильная отдача (haptic feedback)**: отдача при упоре в границу (пределы зума и сетки), тиковый отклик при достижении порога закрытия свайпом и тактильные «всплески» при добавлении в закладки.
  * **Широкий цветовой охват (Display P3)**: поддержка 10-битного широкого цветового охвата для сочной передачи произведений на OLED-экранах.
*  **Глубокий опыт чтения**: отдельный просмотрщик манги и читалка романов с настраиваемой типографикой, продолжением с сохранённого места и фоновой озвучкой через синтез речи (TTS) — как аудиокнига.
*  **Встроенная загрузка и обновление**: многопоточный загрузчик с удалением EXIF-данных, шаблонным именованием файлов и бесшовными фоновыми обновлениями APK с поддержкой Shizuku.
*  **Конфиденциальность и безопасность**: PIN-блокировка, биометрическая аутентификация, незаметное размытие снимка приложения в списке недавних задач и очистка URL от трекинговых параметров.
*  **Трёхъязычная локализация**: полная поддержка японского (日本語), английского и корейского (한국어) языков.

---

## Скриншоты

<table align="center">
  <tr>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/1.png"
        alt="Главный экран Palleria"
        width="160"
      />
      <br />
      <strong>Главная и лента</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/2.png"
        alt="Экран поиска Palleria"
        width="160"
      />
      <br />
      <strong>Обзор и поиск</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/3.png"
        alt="Экран рейтингов Palleria"
        width="160"
      />
      <br />
      <strong>Рейтинги</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/5.png"
        alt="Экран профиля пользователя Palleria"
        width="160"
      />
      <br />
      <strong>Профиль художника</strong>
    </td>
  </tr>
</table>

---

## Обзор возможностей

### Просмотр работ и медиа
* **Обзор (Explore)**: рекомендательные ленты, ежедневные/еженедельные/ежемесячные рейтинги и новейшие работы авторов, на которых вы подписаны.
* **Вертикальная лента Shorts**: иммерсивная полноэкранная вертикальная лента со свайпами для открытия новых иллюстраций.
* **Анимации Ugoira**: энергосберегающий декодер со «скользящим окном», переиспользованием буферов `inBitmap` и экспортом в WebP, APNG, GIF или MP4.
* **Манга и комиксы**: два режима чтения (горизонтальный и вертикальный вебтун), автоматическое определение ориентации страниц и перелистывание кнопками громкости.
* **Романы и TTS-аудиокниги**: настраиваемая типографика, навигация по сериям, автосохранение прогресса чтения и фоновая TTS-озвучка с таймером сна.
* **Полноэкранный просмотрщик**: зум щипком (до 6x) с сопротивлением при упоре в границу, закрытие свайпом с тактильными «тиками» и быстрый предпросмотр (peek).

### Поиск и обнаружение
* **Исчерпывающий поиск**: поиск по тегам, точное совпадение тегов, названиям, описаниям или авторам.
* **Умная фильтрация**: фильтры по типу работы (иллюстрация, манга, Ugoira, роман), возрастному ограничению (все возрасты, R-18, R-18G), порогу количества закладок и диапазону дат.
* **Список отслеживания тегов**: отслеживайте любимые теги и художников с быстрым доступом в одно касание.

### Загрузки и управление медиа
* **Умные загрузки**: сохранение отдельных страниц или целых многостраничных наборов с настраиваемым количеством параллельных воркеров.
* **Незаметное побайтовое возобновление**: автоматическое восстановление после сетевых сбоев через HTTP 206 `Range`-запросы.
* **Пользовательский путь и SAF**: хранение изображений в пользовательских каталогах SAF или в стандартном альбоме Pictures.
* **Удаление EXIF ради приватности**: удаляет персональные метаданные из загружаемых файлов перед сохранением.
* **Умные шаблоны имён файлов**: форматирование имён файлов по пользовательским шаблонам (`{id}`, `{title}`, `{artist}`, `{page}`).

### Микро-UX и кастомизация
* **Темы**: системная по умолчанию, светлая, тёмная, AMOLED «чистый чёрный» и динамический цвет, подстраивающийся под акценты произведения.
* **Адаптивные макеты**: от 1 до 4 настраиваемых столбцов сетки для телефонов, складных устройств и планшетов.
* **Фильтры скрытия**: фильтрация нежелательных тегов, пользователей и ID работ в лентах и результатах поиска.
* **Стикеры и реакции**: просмотр и отправка стикеров-реакций Pixiv в комментариях.
* **Резервное копирование и восстановление**: экспорт и импорт всех настроек и локальной базы данных в формате JSON.

---

## Установка

### Репозиторий F-Droid (рекомендуется)

Добавьте официальный репозиторий Palleria в F-Droid или любой совместимый клиент (Droid-ify, Neo Store):

```text
https://yunfi.f5.si/Palleria/repo/
```

<p align="left">

[![Добавить репозиторий в F-Droid](https://img.shields.io/badge/F--Droid-Add%20Repository-1976D2.svg?style=for-the-badge&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo?fingerprint=28A7F64F373AC1AD5FBB4822870E2E07B2B204C7EC71E58CE40F9D54EF2727D9)

</p>

### GitHub Releases

Скачайте последний автономный APK напрямую из GitHub Releases:

<p align="left">

[![Скачать APK](https://img.shields.io/badge/GitHub-Download%20Latest%20APK-181717.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/releases/latest)

</p>

### Системные требования

* **ОС**: Android 7.0 (Nougat, API 24) или новее (целевая версия — Android 16, API 36)
* **Аккаунт**: аккаунт Pixiv (вход через веб или refresh-токен)
* **Сеть**: активное подключение к интернету (поддерживаются пользовательский прокси для изображений и DoH)

---

## Технологический стек

| Слой | Технологии |
| :--- | :--- |
| **Языки** | Kotlin 2.x, Rust (1.80+) |
| **UI-фреймворк** | Jetpack Compose, Compose Foundation и Material 3 |
| **Дизайн-система** | Miuix KMP (гайдлайны дизайна Xiaomi HyperOS / MIUI) |
| **Нативный движок** | Rust (`pixiv-api`) через UniFFI, асинхронный рантайм Tokio |
| **Сеть** | OkHttp 4 / 5, DNS over HTTPS (DoH), прогрев ConnectionPool |
| **Конвейер изображений** | Coil 3, аппаратные битмапы, цветовой охват Display P3, переиспользование `inBitmap` |
| **Хранение данных** | Room (SQLite с индексами миграций), Jetpack DataStore Preferences, AtomicFile |
| **Фоновая синхронизация** | WorkManager (периодическая дельта-синхронизация по ETag и фоновые обновления) |
| **Системная интеграция** | Shizuku API (бесшумная установка без root), Android SAF, Glance AppWidget |

---

## Сборка из исходного кода

### Предварительные требования

1. **Android Studio**: Ladybug / Meerkat или новее
2. **JDK**: Java 17 или Java 21
3. **Android NDK**: NDK 26 или новее, установленный через Android SDK Manager
4. **Инструментарий Rust**: Stable Rust с `cargo-ndk`:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
   cargo install cargo-ndk
   ```

### Клонирование и компиляция

```bash
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
```

**Сборка отладочного (debug) APK:**

```bash
# Linux / macOS
./gradlew :app:assembleDebug

# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

**Сборка релизного (release) APK:**

```bash
# Linux / macOS
./gradlew :app:assembleRelease

# Windows PowerShell
.\gradlew.bat :app:assembleRelease
```

Собранные APK будут находиться в:
```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

### Подпись релизных сборок

Чтобы подписывать релизные сборки, настройте следующие переменные окружения:

```powershell
$env:KEYSTORE_PATH = "C:\path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "your-key-alias"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat :app:assembleRelease
```

---

## Метрики и сообщество

<!-- METRICS-STATS:START -->
<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=yunfie-twitter&repo=Palleria&theme=transparent" alt="Статистика репозитория Palleria" />
</p>

<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria/stargazers">
    <img src="https://img.shields.io/badge/Stars-10-blue.svg?style=for-the-badge&logo=github" alt="Звёзды: 10" />
  </a>
  <a href="https://github.com/yunfie-twitter/Palleria/releases">
    <img src="https://img.shields.io/badge/Total%20Downloads-1000-2ea44f.svg?style=for-the-badge&logo=github" alt="Всего загрузок: 1000" />
  </a>
  <a href="https://github.com/yunfie-twitter/Palleria/network/members">
    <img src="https://img.shields.io/badge/Forks-4-orange.svg?style=for-the-badge&logo=github" alt="Форки: 4" />
  </a>
</p>
<!-- METRICS-STATS:END -->

---

## Участники

<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=yunfie-twitter/Palleria" alt="Участники" />
  </a>
</p>

---

## Участие в разработке

Любой вклад, сообщения об ошибках, предложения новых функций и улучшения локализации очень приветствуются!

1. Сделайте форк репозитория и создайте ветку для своей функции (`git checkout -b feat/my-feature`).
2. Перед коммитом запустите проверки стиля кода и юнит-тесты:
   ```bash
   ./gradlew ktlintCheck detekt testDebugUnitTest
   ```
3. Закоммитьте изменения и откройте Pull Request в ветку `main`.

<p align="left">

[![Сообщить о проблеме](https://img.shields.io/badge/GitHub-Report%20Issue-1F883D.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/issues)
[![Pull Requests](https://img.shields.io/badge/GitHub-Pull%20Requests-8250DF.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/pulls)

</p>

---

## Отказ от ответственности

Palleria — неофициальное приложение с открытым исходным кодом и не связано с Pixiv Inc. Все товарные знаки, логотипы и материалы произведений Pixiv принадлежат их правообладателям. Пожалуйста, уважайте Условия предоставления услуг (Terms of Service) Pixiv при использовании этого приложения.

---

## Лицензия

Palleria распространяется по лицензии [GNU General Public License v3.0 (GPL-3.0-only)](LICENSE).

<p align="center">
  Создано с заботой — <strong>ゆんふぃ</strong>
</p>
