# Let's Play - Party Games

## 프로젝트 개요
연말 파티용 다트 게임 (웹 전용)
- **목적:** 파티 현장에서 여러 사람이 즐길 수 있는 게임
- **게임:** 다트 게임 (Dart Game)
- **제작:** Eden.ryu & Claude

## 접속 URL
- **게임:** https://happyrym.github.io/letsplay/
- **Wall of Shame:** https://happyrym.github.io/letsplay/shame.html
- **QR 코드:** `web/qr.png`

## 게임 플레이

### 기본 규칙
| 항목 | 값 |
|------|-----|
| 제한 시간 | 10초 |
| 다트 개수 | 3개 |
| 최대 점수 | 180점 (Triple 20 x 3) |
| 조작 | 다트 잡고 위로 스와이프 |

### 점수표
| 영역 | 점수 |
|------|------|
| Bullseye (정중앙) | 50점 |
| Bull (중앙 외곽) | 25점 |
| Triple Ring | 기본 x 3 |
| Double Ring | 기본 x 2 |
| Single | 1~20점 |

### 보너스 라운드 🔥
- **발동 조건:** 180점 달성 OR Bullseye 3번
- **효과:** 남은 시간 동안 무제한 다트
- **점수 형식:** `180.050` (기본점수.보너스점수)

## 프로젝트 구조

```
letsplay/
├── web/
│   ├── index.html          # dart.html로 리다이렉트
│   ├── dart.html           # 메인 게임
│   ├── shame.html          # Wall of Shame
│   ├── abuser.js           # 어뷰저 탐지
│   ├── logo.svg            # Buzzvi 로고
│   └── qr.png              # 접속용 QR 코드
├── .github/workflows/
│   └── deploy.yml          # GitHub Pages 자동 배포
├── README.md
└── CLAUDE.md
```

## 기술 스택
- **프레임워크:** HTML5 Canvas + Vanilla JavaScript
- **배포:** GitHub Pages (자동 배포)
- **DB:** Firebase Firestore (실시간 동기화)
- **그래픽:** High DPI Canvas (Retina 지원)
- **반응형:** 세로 높이 기준 비율 계산

## Firebase 설정

### 프로젝트
- **Project ID:** `letsplay-party`

### Firestore 컬렉션 구조 (날짜별)
```
games/
  └── 2024-12-25/
      └── scores/
          └── {docId}
              ├── _n: "PLAYER"      # 이름 (난독화)
              ├── _v: 180.050       # 점수 (난독화)
              ├── _t: Timestamp     # 시간
              ├── _d: 8500          # 플레이 시간(ms)
              ├── _p: [{x, y}, ...] # 다트 좌표
              ├── _i: [1200, 800]   # 던지기 간격(ms)
              ├── _b: 50            # 보너스 점수
              └── score, pts, ...   # 더미 데이터
```

## 주요 기능

### 신규 사용자 플로우
1. 튜토리얼 (3단계: 파워→다트→타이머)
2. 연습 게임 플레이
3. 이름 입력
4. Ready 화면 → 본게임

### 기존 사용자 플로우
1. Ready 화면 (이름 저장됨)
2. 게임 시작
3. 결과 + 리더보드

### 어뷰저 탐지
| 탐지 항목 | 방법 |
|----------|------|
| Honeypot 함수 | `submitScore()`, `cheat()` 등 호출 시 |
| 게임 미진행 | `gameState.started` 체크 |
| 불가능 점수 | 180점 초과, 음수 |
| 스피드핵 | 0.5초 미만 플레이 |

### 개발자 모드 (TEST_MODE)
- **활성화:** Buzzvi 로고 10번 탭 (3초 내)
- **효과:** 
  - 다트 점수 60점 고정
  - 0점도 보너스 발동
  - 별도 DB (`games_test/`)

## 숨겨진 기능
- 로고 10번 탭 → TEST_MODE
- 도움말 버튼 (?) → 게임 방법
- 파워 슬라이더 → 던지기 세기 조절 (localStorage 저장)
