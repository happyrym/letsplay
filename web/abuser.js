/**
 * Abuser Detection System
 * 어뷰저 탐지 및 명예의 전당 등록 시스템
 */

const AbuserDetector = {
    SHAME_KEY: 'wallOfShame',

    // 어뷰저 등록
    registerAbuser(name, reason, details = {}) {
        const shameList = JSON.parse(localStorage.getItem(this.SHAME_KEY) || '[]');

        const abuser = {
            name: name || 'Anonymous Cheater',
            reason: reason,
            timestamp: new Date().toISOString(),
            ...details
        };

        shameList.push(abuser);
        localStorage.setItem(this.SHAME_KEY, JSON.stringify(shameList));

        // Firebase에 저장
        this.saveToFirebase(abuser);

        // 알러트 효과!
        this.playShameAlert();
        this.showShameOverlay(abuser);

        console.log('%c🚨 CHEATER DETECTED! 🚨', 'color: red; font-size: 24px; font-weight: bold;');
        console.log('%cYou have been added to the Wall of Shame!', 'color: red; font-size: 16px;');

        return abuser;
    },

    // Firebase에 어뷰저 저장
    async saveToFirebase(abuser) {
        try {
            if (typeof db !== 'undefined' && typeof firebase !== 'undefined') {
                await db.collection('wall_of_shame').add({
                    ...abuser,
                    timestamp: firebase.firestore.FieldValue.serverTimestamp()
                });
                console.log('Abuser saved to Firebase');
            }
        } catch (error) {
            console.error('Failed to save abuser to Firebase:', error);
        }
    },

    // 알러트 사운드 (3초)
    playShameAlert() {
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();

        // 경고음 시퀀스 (3초 동안)
        const playBeep = (freq, startTime, duration) => {
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();

            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);

            oscillator.frequency.value = freq;
            oscillator.type = 'square';

            gainNode.gain.setValueAtTime(0.3, startTime);
            gainNode.gain.exponentialRampToValueAtTime(0.01, startTime + duration);

            oscillator.start(startTime);
            oscillator.stop(startTime + duration);
        };

        const now = audioContext.currentTime;

        // 3초 동안 경고음 반복
        for (let i = 0; i < 6; i++) {
            playBeep(800, now + i * 0.5, 0.2);
            playBeep(600, now + i * 0.5 + 0.25, 0.2);
        }
    },

    // 화면에 어뷰저 알림 표시
    showShameOverlay(abuser) {
        const overlay = document.createElement('div');
        overlay.id = 'shameOverlay';
        overlay.innerHTML = `
            <div class="shame-alert">
                <div class="shame-icon">💀</div>
                <div class="shame-title">CHEATER DETECTED!</div>
                <div class="shame-message">You've been added to the</div>
                <div class="shame-wall">Wall of Shame</div>
                <div class="shame-name">${this.escapeHtml(abuser.name)}</div>
                <a href="shame.html" class="shame-link">View Hall of Shame →</a>
            </div>
        `;

        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.95);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 10000;
            animation: fadeIn 0.3s ease;
        `;

        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes shake {
                0%, 100% { transform: translateX(0); }
                25% { transform: translateX(-10px); }
                75% { transform: translateX(10px); }
            }
            @keyframes pulse {
                0%, 100% { transform: scale(1); }
                50% { transform: scale(1.1); }
            }
            .shame-alert {
                text-align: center;
                color: white;
                animation: shake 0.5s ease-in-out 3;
            }
            .shame-icon {
                font-size: 100px;
                animation: pulse 0.5s infinite;
            }
            .shame-title {
                font-size: 48px;
                font-weight: bold;
                color: #e74c3c;
                margin: 20px 0;
                text-shadow: 0 0 20px rgba(231, 76, 60, 0.8);
            }
            .shame-message {
                font-size: 24px;
                color: rgba(255, 255, 255, 0.7);
            }
            .shame-wall {
                font-size: 36px;
                font-weight: bold;
                color: #e74c3c;
                margin: 10px 0 30px;
            }
            .shame-name {
                font-size: 28px;
                color: #f39c12;
                margin-bottom: 30px;
            }
            .shame-link {
                display: inline-block;
                padding: 15px 30px;
                background: #e74c3c;
                color: white;
                text-decoration: none;
                border-radius: 10px;
                font-size: 18px;
                margin-top: 20px;
            }
            .shame-link:hover {
                background: #c0392b;
            }
        `;

        document.head.appendChild(style);
        document.body.appendChild(overlay);

        // 5초 후 자동으로 닫히게 (클릭해도 닫힘)
        overlay.addEventListener('click', () => overlay.remove());
        setTimeout(() => overlay.remove(), 5000);
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    // 게임 상태 추적용
    gameState: {
        started: false,
        startTime: 0,
        interactions: 0,
        dragEvents: 0,
        touchPoints: [],      // 터치 좌표 기록
        dragPaths: [],        // 드래그 궤적 기록
        touchRadii: [],       // 터치 영역 크기 기록
        dartLandingPositions: []  // 다트 착탄 위치 기록
    },

    resetGameState() {
        this.gameState = {
            started: false,
            startTime: 0,
            interactions: 0,
            dragEvents: 0,
            touchPoints: [],
            dragPaths: [],
            touchRadii: [],
            dartLandingPositions: []
        };
    },

    startGame() {
        this.gameState.started = true;
        this.gameState.startTime = Date.now();
    },

    recordInteraction() {
        this.gameState.interactions++;
    },

    recordDrag() {
        this.gameState.dragEvents++;
    },

    // 터치 좌표 기록
    recordTouch(x, y, radiusX = 0, radiusY = 0) {
        this.gameState.touchPoints.push({ x, y, time: Date.now() });
        this.gameState.touchRadii.push({ radiusX, radiusY });
    },

    // 드래그 경로 기록 (여러 포인트)
    recordDragPath(points) {
        if (points && points.length > 0) {
            this.gameState.dragPaths.push(points);
        }
    },

    // 다트 착탄 위치 기록
    recordDartLanding(normalizedX, normalizedY) {
        this.gameState.dartLandingPositions.push({ x: normalizedX, y: normalizedY });
    },

    // 터치 좌표 분산 계산
    calculateTouchVariance() {
        const points = this.gameState.touchPoints;
        if (points.length < 3) return { valid: true };

        // X, Y 각각의 분산 계산
        const xValues = points.map(p => p.x);
        const yValues = points.map(p => p.y);

        const xMean = xValues.reduce((a, b) => a + b, 0) / xValues.length;
        const yMean = yValues.reduce((a, b) => a + b, 0) / yValues.length;

        const xVariance = xValues.reduce((sum, x) => sum + Math.pow(x - xMean, 2), 0) / xValues.length;
        const yVariance = yValues.reduce((sum, y) => sum + Math.pow(y - yMean, 2), 0) / yValues.length;

        // 분산이 거의 0이면 봇 의심 (항상 같은 위치 터치)
        if (xVariance < 1 && yVariance < 1) {
            return {
                valid: false,
                reason: 'bot_touch_pattern',
                detail: `Touch variance too low (x: ${xVariance.toFixed(2)}, y: ${yVariance.toFixed(2)})`
            };
        }

        return { valid: true };
    },

    // 드래그 궤적 직선도 분석
    analyzeDragLinearity() {
        const paths = this.gameState.dragPaths;
        if (paths.length < 1) return { valid: true };

        let perfectLineCount = 0;

        for (const path of paths) {
            if (path.length < 5) continue;

            // 시작점과 끝점을 잇는 직선으로부터의 평균 거리 계산
            const start = path[0];
            const end = path[path.length - 1];

            const lineLength = Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
            if (lineLength < 50) continue; // 너무 짧은 드래그는 무시

            let totalDeviation = 0;
            for (let i = 1; i < path.length - 1; i++) {
                const point = path[i];
                // 점과 직선 사이의 거리
                const deviation = Math.abs(
                    (end.y - start.y) * point.x - (end.x - start.x) * point.y + end.x * start.y - end.y * start.x
                ) / lineLength;
                totalDeviation += deviation;
            }

            const avgDeviation = totalDeviation / (path.length - 2);

            // 평균 편차가 2픽셀 미만이면 완벽한 직선 (봇 의심)
            if (avgDeviation < 2) {
                perfectLineCount++;
            }
        }

        // 모든 드래그가 완벽한 직선이면 봇
        if (paths.length >= 2 && perfectLineCount === paths.length) {
            return {
                valid: false,
                reason: 'bot_drag_pattern',
                detail: `All ${paths.length} drags are perfect lines`
            };
        }

        return { valid: true };
    },

    // 가상 터치 감지 (radiusX/Y가 항상 0)
    detectVirtualTouch() {
        const radii = this.gameState.touchRadii;
        if (radii.length < 3) return { valid: true };

        const zeroRadiusCount = radii.filter(r => r.radiusX === 0 && r.radiusY === 0).length;
        const zeroRatio = zeroRadiusCount / radii.length;

        // 모든 터치가 radius 0이면 가상 터치 (봇 의심)
        if (zeroRatio === 1 && radii.length >= 5) {
            return {
                valid: false,
                reason: 'virtual_touch',
                detail: `All ${radii.length} touches have zero radius (simulated)`
            };
        }

        return { valid: true };
    },

    // 다트 착탄 위치가 완전히 동일한지 검사
    // 사람 손으로는 정확히 같은 위치에 던지기가 불가능
    checkIdenticalDartPositions() {
        const positions = this.gameState.dartLandingPositions;
        if (positions.length < 2) return { valid: true };

        // 모든 위치 쌍을 비교하여 완전히 동일한 위치가 있는지 확인
        for (let i = 0; i < positions.length; i++) {
            for (let j = i + 1; j < positions.length; j++) {
                const pos1 = positions[i];
                const pos2 = positions[j];
                
                // 완전히 동일한 위치
                if (pos1.x === pos2.x && pos1.y === pos2.y) {
                    return {
                        valid: false,
                        reason: 'identical_dart_position',
                        detail: `Dart ${i + 1} and ${j + 1} landed at identical position (${pos1.x}, ${pos1.y})`
                    };
                }
            }
        }

        return { valid: true };
    },

    // 점수 검증 (파티용으로 기본 체크만)
    validateScore(score, game, maxScore) {
        // 1. 불가능한 점수 체크 (최대 점수 초과)
        if (score > maxScore) {
            return {
                valid: false,
                reason: 'impossible_score',
                detail: `Score ${score} exceeds max ${maxScore}`
            };
        }

        // 2. 음수 점수 체크
        if (score < 0) {
            return {
                valid: false,
                reason: 'impossible_score',
                detail: `Negative score: ${score}`
            };
        }

        // 3. 게임 시작 여부 체크
        if (!this.gameState.started) {
            return {
                valid: false,
                reason: 'no_gameplay',
                detail: 'Score submitted without starting game'
            };
        }

        // 4. 최소 플레이 시간 (0.5초) - 스피드핵 방지
        const playTime = Date.now() - this.gameState.startTime;
        if (playTime < 500 && this.gameState.started) {
            return {
                valid: false,
                reason: 'speed_hack',
                detail: `Game completed in ${playTime}ms`
            };
        }

        return { valid: true };
    }
};

// Honeypot 함수들 - 스크립트가 찾아서 호출하도록 유도
window._submitScore = function(name, score) {
    AbuserDetector.registerAbuser(name || 'Script Kiddie', 'honeypot', {
        attemptedScore: score,
        game: 'Unknown'
    });
    return { success: false, message: 'Nice try! 🎅' };
};

window.submitScore = window._submitScore;
window.cheat = window._submitScore;
window.hack = window._submitScore;
window.setScore = window._submitScore;
window.addScore = window._submitScore;

// 콘솔에 함정 메시지 표시
console.log('%c🎄 Looking for cheats? 🎄', 'color: green; font-size: 20px;');
console.log('%cTry: submitScore("YourName", 9999)', 'color: gray; font-size: 14px;');

// Export for modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AbuserDetector;
}
