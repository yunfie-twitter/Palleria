# Palleria

[English](README.md) · [日本語](README_ja.md) · [한국어](README_ko.md)

<p align="center">
  <img
    src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/icon.png"
    alt="Palleria 아이콘"
    width="160"
  />
</p>

<h3 align="center">
  Android를 위한 빠르고 현대적인 오픈소스 Pixiv 클라이언트
</h3>

<p align="center">
  Jetpack Compose, Miuix UI, Rust 네이티브 코어로 일러스트, 만화, 소설을 부드럽게 탐색하세요.
</p>

<p align="center">

[![License](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Rust](https://img.shields.io/badge/Rust-Native%20Core-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Miuix KMP](https://img.shields.io/badge/Design-Miuix%20KMP-FF6900.svg?style=flat-square)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![F-Droid](https://img.shields.io/badge/F--Droid-Repository-1976D2.svg?style=flat-square&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo/)
[![GitHub Release](https://img.shields.io/github/v/release/yunfie-twitter/Palleria?style=flat-square&logo=github&label=Release)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/yunfie-twitter/Palleria/total?style=flat-square&logo=github&label=Downloads)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Stars](https://img.shields.io/github/stars/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/issues)
[![Architecture diagram](https://gitdiagram.com/diagram-badge.svg)](https://gitdiagram.com/diagram/yunfie-twitter/palleria?utm_source=readme&utm_medium=badge)
</p>

---

## 소개

**Palleria**는 모바일에서 쾌적한 Pixiv 탐색 경험을 제공하는 무료·개인정보 보호 중심의 오픈소스 Android 앱입니다.

Jetpack Compose와 Miuix 디자인, UniFFI를 통한 Rust 네이티브 백엔드를 기반으로 빠른 성능, 낮은 배터리 소모, 풍부한 상호작용, 종단 간 암호화 동기화를 제공합니다.

> [!IMPORTANT]
> Palleria는 비공식 Pixiv 클라이언트이며 Pixiv Inc.와 제휴·승인·관련이 없습니다.

## 주요 특징

- **고성능 Rust 코어**: UniFFI 네이티브 엔진으로 우고이라 디코딩, APNG/WebP/MP4 변환, 데이터 처리를 가속합니다.
- **Miuix 및 Material You UI**: 부드러운 애니메이션, AMOLED 및 작품 색상 테마, 사용자 지정 다중 열 그리드를 지원합니다.
- **빠른 탐색을 위한 최적화**: 시작 중 DNS/TLS 연결 준비, 차등·ETag 동기화, 스크롤 정지 위치 예측 프리페치, 중단된 다운로드 재개, 만화 방향에 맞춘 적응형 선읽기를 제공합니다.
- **PallaSync**: 북마크, 열람 기록, 설정, 뮤트를 Android 기기 및 브라우저 확장 프로그램과 종단 간 암호화로 동기화합니다.
- **독서 기능**: 만화 뷰어, 소설 리더, 글꼴 설정, 읽던 위치 저장, 백그라운드 TTS 오디오북을 제공합니다.
- **다운로드 및 업데이트**: 병렬 다운로드, EXIF 개인정보 제거, 파일명 템플릿, Shizuku를 통한 APK 업데이트를 지원합니다.
- **개인정보 및 보안**: PIN·생체 인증 잠금, 최근 앱 미리보기 흐림 처리, 추적 URL 정리를 제공합니다.
- **3개 언어 지원**: 일본어, 영어, 한국어.

## 스크린샷

<table align="center">
  <tr>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/1.png" alt="홈 화면" width="160" /><br /><strong>홈 및 피드</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/2.png" alt="검색 화면" width="160" /><br /><strong>탐색 및 검색</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/3.png" alt="랭킹 화면" width="160" /><br /><strong>랭킹</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/5.png" alt="사용자 프로필 화면" width="160" /><br /><strong>작가 프로필</strong></td>
  </tr>
</table>

## 기능

### 작품 및 미디어 탐색
- 추천 피드, 일간·주간·월간 랭킹, 팔로우한 작가의 최신 작품을 탐색합니다.
- 세로 스와이프로 탐색하는 전체 화면 쇼츠 피드를 제공합니다.
- 전력 절약형 우고이라 디코더와 WebP/APNG/GIF/MP4 내보내기를 지원합니다.
- 가로·세로 만화 보기, 페이지 방향 자동 감지, 음량 키 페이지 넘기기를 지원합니다.
- 소설 시리즈 탐색, 글꼴 설정, 읽기 진행 저장, 취침 타이머가 있는 백그라운드 TTS를 제공합니다.
- 최대 6배 핀치 줌, 스와이프 닫기, 빠른 미리보기를 지원합니다.

### 검색 및 발견
- 태그, 정확히 일치하는 태그, 제목, 설명, 작가명으로 검색합니다.
- 작품 유형, 연령 제한, 북마크 수, 날짜로 필터링합니다.
- 태그와 작가를 저장해 빠르게 여는 워치리스트를 제공합니다.

### 다운로드 및 미디어 관리
- 작품의 개별 페이지 또는 여러 페이지를 한 번에 저장하고 동시 작업 수를 설정합니다.
- HTTP 206 `Range` 요청으로 중단된 다운로드를 이어받습니다.
- SAF 사용자 지정 폴더 또는 기본 Pictures 위치에 저장합니다.
- 저장 전 EXIF 개인정보를 제거하고 `{id}`, `{title}`, `{artist}`, `{page}` 템플릿으로 파일명을 지정합니다.

### PallaSync
- 제3자 추적 없이 종단 간 암호화로 동기화합니다.
- Android 앱과 브라우저 확장 프로그램 간 북마크, 열람·검색 기록, 설정, 뮤트를 동기화합니다.
- QR 코드 또는 보안 페어링 문구로 연결합니다.

### UI 및 사용자 지정
- 시스템, 라이트, 다크, AMOLED 블랙, 작품 색상 테마를 제공합니다.
- 휴대폰, 폴더블, 태블릿에 맞게 1~4열 그리드를 설정합니다.
- 피드와 검색 결과에서 태그·사용자·작품 ID를 뮤트합니다.
- 댓글의 Pixiv 스탬프를 보고 보낼 수 있습니다.
- 설정과 로컬 데이터베이스를 JSON으로 백업·복원합니다.

## 설치

### F-Droid 저장소 (권장)

공식 Palleria 저장소를 F-Droid 또는 호환 클라이언트(Droid-ify, Neo Store 등)에 추가하세요.

```text
https://yunfi.f5.si/Palleria/repo/
```

[![F-Droid에 추가](https://img.shields.io/badge/F--Droid-저장소%20추가-1976D2.svg?style=for-the-badge&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo?fingerprint=28A7F64F373AC1AD5FBB4822870E2E07B2B204C7EC71E58CE40F9D54EF2727D9)

### GitHub Releases

[최신 APK 다운로드](https://github.com/yunfie-twitter/Palleria/releases/latest)

### 시스템 요구 사항

- **OS**: Android 7.0(API 24) 이상. Target SDK는 Android 16(API 36)입니다.
- **계정**: Pixiv 계정(Web 로그인 또는 Refresh Token).
- **네트워크**: 인터넷 연결(사용자 지정 이미지 프록시 및 DoH 지원).

## 기술 스택

| 분야 | 기술 |
| :--- | :--- |
| 언어 | Kotlin 2.x, Rust 1.80+ |
| UI | Jetpack Compose, Compose Foundation, Material 3 |
| 디자인 시스템 | Miuix KMP(Xiaomi HyperOS / MIUI 디자인 가이드라인) |
| 네이티브 엔진 | UniFFI의 Rust (`pixiv-api`), Tokio |
| 네트워킹 | OkHttp 4 / 5, DoH, 연결 풀 사전 준비 |
| 이미지 | Coil 3, Hardware Bitmap, Display P3, `inBitmap` 재사용 |
| 저장소 | Room, Jetpack DataStore Preferences, AtomicFile |
| 백그라운드 동기화 | WorkManager(ETag 차등 동기화 및 업데이트) |
| 시스템 통합 | Shizuku, Android SAF, Glance AppWidget |

## 소스에서 빌드

### 사전 요구 사항

1. Android Studio Ladybug / Meerkat 이상
2. JDK 17 또는 21
3. Android SDK Manager에서 NDK 26 이상 설치
4. Stable Rust 및 `cargo-ndk`

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

### 복제 및 컴파일

```bash
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
```

```bash
# Debug APK (Linux / macOS)
./gradlew :app:assembleDebug

# Release APK (Linux / macOS)
./gradlew :app:assembleRelease
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용하세요. APK는 `app/build/outputs/apk/debug/` 및 `app/build/outputs/apk/release/`에 생성됩니다.

### Release 서명

Release 빌드 서명에 다음 환경 변수를 설정하세요.

```powershell
$env:KEYSTORE_PATH = "C:\path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "your-key-alias"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat :app:assembleRelease
```

## 커뮤니티 및 통계

<!-- METRICS-STATS:START -->
<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=yunfie-twitter&repo=Palleria&theme=transparent" alt="Palleria 저장소 통계" />
</p>
<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria/stargazers"><img src="https://img.shields.io/badge/Stars-10-blue.svg?style=for-the-badge&logo=github" alt="Stars: 10" /></a>
  <a href="https://github.com/yunfie-twitter/Palleria/releases"><img src="https://img.shields.io/badge/Total%20Downloads-1000-2ea44f.svg?style=for-the-badge&logo=github" alt="Total Downloads: 1000" /></a>
  <a href="https://github.com/yunfie-twitter/Palleria/network/members"><img src="https://img.shields.io/badge/Forks-4-orange.svg?style=for-the-badge&logo=github" alt="Forks: 4" /></a>
</p>
<!-- METRICS-STATS:END -->

## 기여자

<p align="center"><a href="https://github.com/yunfie-twitter/Palleria/graphs/contributors"><img src="https://contrib.rocks/image?repo=yunfie-twitter/Palleria" alt="기여자" /></a></p>

## 기여하기

코드 기여, 버그 제보, 기능 제안, 번역 개선을 환영합니다.

1. 저장소를 Fork하고 기능 브랜치를 만드세요(`git checkout -b feat/my-feature`).
2. 커밋 전에 스타일 검사와 단위 테스트를 실행하세요.
   ```bash
   ./gradlew ktlintCheck detekt testDebugUnitTest
   ```
3. 변경 사항을 커밋하고 `main`을 대상으로 Pull Request를 열어 주세요.

[이슈 제보](https://github.com/yunfie-twitter/Palleria/issues) · [Pull Request](https://github.com/yunfie-twitter/Palleria/pulls)

## 면책 조항

Palleria는 Pixiv Inc.와 관련이 없는 비공식 오픈소스 앱입니다. Pixiv 상표, 로고, 작품 자산의 권리는 각 소유자에게 있습니다. 앱을 사용할 때 Pixiv 이용 약관을 준수해 주세요.

## 라이선스

Palleria는 [GNU General Public License v3.0(GPL-3.0-only)](LICENSE)으로 배포됩니다.

<p align="center">ゆんふぃ가 정성을 담아 만들었습니다</p>
