# Let's Play! - Christmas Party Games

연말 파티용 웹/앱 게임 프로젝트

## Demo

**Web (GitHub Pages):** [https://[username].github.io/letsplay/](https://[username].github.io/letsplay/)

## Games

### Punch Game
10초 안에 망치로 펀치머신을 두드려 최고 점수를 기록하세요!
- 속도 + 정확도 기반 점수 계산 (최대 1000점)
- 드래그 속도 감지 및 타겟 정확도 측정
- 파티클 이펙트 및 애니메이션

### Dart Game
다트를 던져 과녁을 맞춰 점수를 획득하세요!
- 터치/드래그 기반 다트 던지기
- 과녁 정확도 기반 점수 계산

## Tech Stack

### Web
- **HTML5 Canvas** + **Vanilla JavaScript**
- **GitHub Pages** 배포
- **LocalStorage** 기반 리더보드

### Android
- **Kotlin** + **Jetpack Compose**
- **Nearby Connections API** (P2P 통신)
- 태블릿(게임) ↔ 스마트폰(리더보드) 연동

## Project Structure

```
letsplay/
├── android/               # Android 앱
│   ├── app/               # 메인 게임 앱 (태블릿)
│   └── leaderboard/       # 리더보드 디스플레이 앱 (스마트폰)
├── web/                   # 웹 게임 (GitHub Pages)
│   ├── index.html         # 메인 메뉴
│   ├── punch.html         # 펀치 게임
│   ├── dart.html          # 다트 게임
│   ├── shame.html         # Wall of Shame (어뷰저 명예의 전당)
│   └── abuser.js          # 어뷰저 탐지 시스템
├── .github/workflows/     # GitHub Actions
│   └── deploy.yml         # Pages 자동 배포
├── CLAUDE.md              # 개발 문서
└── README.md
```

## Features

### Leaderboard
- 로컬 스토리지 기반 Top 10 랭킹
- 이름 입력 (A-Z 키보드 + 크리스마스 이모지)
- 0점일 경우 리더보드 저장 스킵

### Anti-Cheat System
어뷰저 탐지 및 "Wall of Shame" 시스템:
- **Honeypot 함수** - 스크립트 키디 탐지
- **점수 검증** - 불가능한 점수, 스피드핵, 게임플레이 미감지
- **Wall of Shame** - 적발된 어뷰저 명예의 전당
- **경고 효과** - 3초 경고음 + 화면 오버레이

## Deployment

### GitHub Pages (Web)
```bash
# .github/workflows/deploy.yml 자동 배포
# 또는 Settings → Pages → Branch: main, Folder: /web
```

### Android
```bash
cd android
./gradlew assembleDebug
```

## License

이 프로젝트는 연말 파티 전용으로 제작되었습니다.

**Icon Credits:**
- Hammer & Punch Machine icons from [UXWing](https://uxwing.com/) (Free for commercial use)
