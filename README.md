# Let's Play! - Party Games 🎉

연말 파티용 다트 게임

## Play Now

**Web:** GitHub Pages에서 바로 플레이 (배포 후 URL 생성)

## Game: Dart 🎯

10초 안에 3개의 다트를 던져 최고 점수를 기록하세요!

- **조작:** 다트를 잡고 위로 스와이프
- **점수:** 과녁 정확도 기반 (최대 180점)
- **리더보드:** Firebase 실시간 동기화

### 점수표
| 영역 | 점수 |
|------|------|
| Bullseye | 50점 |
| Bull | 25점 |
| Triple | 기본 x 3 |
| Double | 기본 x 2 |
| Single | 1~20점 |

## Tech Stack

- **Web:** HTML5 Canvas + Vanilla JavaScript
- **Backend:** Firebase Firestore
- **Deploy:** GitHub Pages + GitHub Actions

## Project Structure

```
letsplay/
├── web/                    # 웹 게임
│   ├── index.html          # 메인 메뉴
│   ├── dart.html           # 다트 게임
│   ├── shame.html          # Wall of Shame
│   └── abuser.js           # 어뷰저 탐지
├── android/                # Android 앱 (개발 중)
│   ├── app/                # 게임 앱
│   └── leaderboard/        # 리더보드 앱
└── .github/workflows/      # CI/CD
```

## Features

- ✅ 다트 게임 (10초, 3다트)
- ✅ Firebase 리더보드 (실시간)
- ✅ 어뷰저 탐지 시스템
- ✅ Wall of Shame (치터 명예의 전당)

## Anti-Cheat System 🛡️

- Honeypot 함수로 스크립트 키디 탐지
- 점수 검증 (최대 점수, 플레이 시간)
- 적발 시 Wall of Shame 등록 + 경고음

## Development

```bash
# 로컬 테스트 (Python)
cd web
python -m http.server 8080

# 브라우저에서 http://localhost:8080 접속
```

## Firebase Setup

1. Firebase Console에서 프로젝트 생성
2. Firestore Database 활성화 (테스트 모드)
3. 웹 앱 등록 후 config 복사
4. `dart.html`의 `firebaseConfig` 수정

## License

Made with ❤️ for Christmas Party 🎄
