# Let's Play - Party Games

## 프로젝트 개요
연말 파티용 게임 어플리케이션
**목적:** 파티 현장에서 여러 사람이 즐길 수 있는 게임
**현재 게임:** 다트 게임 (Dart Game)
**제작:** Eden.ryu

## 접속 URL
- **게임:** https://happyrym.github.io/letsplay/
- **Wall of Shame:** https://happyrym.github.io/letsplay/shame.html
- **QR 코드:** `web/qr.png`

## 게임: 다트 (Dart Game)

### 게임 플레이
- **제한 시간:** 10초
- **다트 개수:** 3개
- **조작:** 다트를 잡고 위로 스와이프해서 던지기
- **점수:** 과녁 정확도 기반 (최대 180점 = Triple 20 x 3)
- **보너스:** 180점 달성 시 보너스 다트 (시간 내 추가 점수 가능)

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
│   ├── index.html             # dart.html로 리다이렉트
│   ├── dart.html              # 다트 게임 + Firebase 연동
│   ├── shame.html             # Wall of Shame (어뷰저 명단)
│   ├── abuser.js              # 어뷰저 탐지 시스템
│   └── qr.png                 # 접속용 QR 코드
├── .github/workflows/
│   └── deploy.yml             # GitHub Pages 자동 배포
├── README.md
└── CLAUDE.md
```

## 기술 스택

### 웹 (web/)
- **프레임워크:** HTML5 Canvas + Vanilla JavaScript
- **배포:** GitHub Pages (자동 배포)
- **리더보드:** Firebase Firestore (실시간 동기화)
- **그래픽:** High DPI Canvas (Retina 디스플레이 지원)

### Android (android/)
- **언어:** Kotlin
- **UI:** Jetpack Compose
- **리더보드:** Firebase Firestore
- **P2P 통신:** Nearby Connections API (태블릿 ↔ 스마트폰)

## Firebase 설정

### 프로젝트 정보
- **Project ID:** `letsplay-party`
- **Firestore Collection:** `dart-scores`

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
// Collection: dart-scores
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
- 터치 스크롤 지원

### Android 리더보드 앱
- Firebase 실시간 동기화
- **관리자 기능:** 타이틀 5번 탭 → 점수 초기화 (숨김 기능)

## 어뷰저 탐지 (Anti-Cheat)

### 탐지 방법
| 공격 방법 | 탐지 여부 | 설명 |
|----------|----------|------|
| Honeypot 함수 호출 | ✅ | `submitScore()`, `cheat()`, `hack()` 등 |
| 게임 안하고 제출 | ✅ | `gameState.started` 체크 |
| 스피드핵 (1초 미만) | ✅ | 플레이 시간 체크 |
| 봇 (완벽한 직선 드래그) | ✅ | 드래그 궤적 분석 |
| 동일 위치 반복 착탄 | ✅ | 착탄 위치 비교 |
| 가상 터치 (radius=0) | ✅ | 터치 영역 크기 체크 |
| Firebase 직접 쓰기 | ❌ | 클라이언트 사이드 한계 |

### Wall of Shame
- 적발된 치터 명예의 전당
- 경고음 + 풀스크린 알림
- URL 직접 접근으로 확인 가능

## 완료된 기능

### 웹 게임
- ✅ 다트 게임 (10초, 3다트, 스와이프 던지기)
- ✅ 180점 보너스 라운드
- ✅ High DPI 캔버스 (선명한 그래픽)
- ✅ 반응형 레이아웃 (세로 기준 비율)
- ✅ Firebase 리더보드 (실시간 Top 10)
- ✅ 이름 입력 키보드 (A-Z + 이모지)
- ✅ 어뷰저 탐지 시스템
- ✅ Wall of Shame 페이지
- ✅ GitHub Actions 자동 배포
- ✅ 접속용 QR 코드

### Android 앱
- ✅ 리더보드 앱 Firebase 연동
- ✅ 실시간 점수 동기화
- ✅ 관리자 점수 초기화 (타이틀 5번 탭)

## 관리자 기능

### 점수 초기화 (Android 앱 전용)
1. 리더보드 앱 실행
2. "🎯 DART LEADERBOARD 🎯" 타이틀을 **5번 연속 탭** (2초 내)
3. 확인 다이얼로그에서 "DELETE ALL" 선택
4. 모든 점수 삭제됨

## 다음 작업

- 🔄 Firebase Security Rules 강화 (서버 사이드 점수 검증)
