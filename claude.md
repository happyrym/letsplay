# Let's Play - Party Games

## 프로젝트 개요
연말 파티용 게임 어플리케이션
**목적:** 파티 현장에서 여러 사람이 즐길 수 있는 게임
**현재 게임:** 다트 게임 (Dart Game)

## 게임: 다트 (Dart Game)

### 게임 플레이
- **제한 시간:** 10초
- **다트 개수:** 3개
- **조작:** 다트를 잡고 위로 스와이프해서 던지기
- **점수:** 과녁 정확도 기반 (최대 180점 = Triple 20 x 3)

### 점수 계산
- **Bullseye (중앙):** 50점
- **Bull (중앙 외곽):** 25점
- **Triple Ring:** 기본 점수 x 3
- **Double Ring:** 기본 점수 x 2
- **Single:** 기본 점수 (1~20)

## 프로젝트 구조

```
letsplay/
├── android/                   # Android 앱
│   ├── app/                   # 메인 게임 앱 (태블릿)
│   ├── leaderboard/           # 리더보드 앱 (스마트폰)
│   │   └── google-services.json
│   └── gradle 설정 파일들
├── web/                       # 웹 게임 (GitHub Pages)
│   ├── index.html             # 메인 메뉴
│   ├── dart.html              # 다트 게임 + Firebase 연동
│   ├── shame.html             # Wall of Shame
│   └── abuser.js              # 어뷰저 탐지 시스템
├── .github/workflows/
│   └── deploy.yml             # GitHub Pages 자동 배포
├── README.md
└── CLAUDE.md
```

## 기술 스택

### 웹 (web/)
- **프레임워크:** HTML5 Canvas + Vanilla JavaScript
- **배포:** GitHub Pages
- **리더보드:** Firebase Firestore (실시간 동기화)

### Android (android/)
- **언어:** Kotlin
- **UI:** Jetpack Compose
- **리더보드:** Firebase Firestore
- **P2P 통신:** Nearby Connections API (태블릿 ↔ 스마트폰)

## Firebase 설정

### 프로젝트 정보
- **Project ID:** `letsplay-party`
- **Firestore Collection:** `dart_scores`

### 웹 설정 (dart.html)
```javascript
const firebaseConfig = {
    apiKey: "AIzaSyBBNFKtvBRE6_wN-8uDIgLKk0uyeCGslXM",
    authDomain: "letsplay-party.firebaseapp.com",
    projectId: "letsplay-party",
    storageBucket: "letsplay-party.firebasestorage.app",
    messagingSenderId: "162068174696",
    appId: "1:162068174696:web:2541b8a4242e919db6a8dd"
};
```

### Android 설정
- **패키지명:** `com.rymin.punch.leaderboard`
- **설정 파일:** `android/leaderboard/google-services.json`

### Firestore 데이터 구조
```javascript
// Collection: dart_scores
{
    name: "Player Name",
    score: 120,
    timestamp: Timestamp
}
```

## 리더보드 시스템

### 웹 (Firebase)
- 실시간 구독 (`onSnapshot`)
- Top 10 자동 정렬 (`orderBy('score', 'desc').limit(10)`)
- 오프라인 시 localStorage 폴백

### 어뷰저 탐지 (Anti-Cheat)
- **Honeypot 함수:** `submitScore`, `cheat`, `hack` 등
- **점수 검증:** 최대 점수 초과, 음수 점수, 스피드핵
- **Wall of Shame:** 적발된 치터 명예의 전당

## 완료된 기능

1. ✅ 다트 게임 (웹)
   - 10초 타이머, 3개 다트
   - 터치/드래그 기반 던지기
   - 과녁 정확도 점수 계산
   - 파티클 이펙트

2. ✅ Firebase 리더보드 (웹)
   - Firestore 실시간 동기화
   - Top 10 랭킹
   - 이름 입력 (A-Z + 이모지)

3. ✅ 어뷰저 탐지 시스템
   - Honeypot 함수
   - 점수/게임플레이 검증
   - Wall of Shame 페이지

4. ✅ GitHub Actions 배포
   - Push 시 자동 배포

## 다음 작업

1. 🔄 Android 앱 Firebase 연동
   - Firestore SDK 추가
   - 점수 저장/조회 구현

2. 🔄 GitHub Pages 설정
   - Settings → Pages → Source: GitHub Actions
