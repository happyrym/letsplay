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

        // 알러트 효과!
        this.playShameAlert();
        this.showShameOverlay(abuser);

        console.log('%c🚨 CHEATER DETECTED! 🚨', 'color: red; font-size: 24px; font-weight: bold;');
        console.log('%cYou have been added to the Wall of Shame!', 'color: red; font-size: 16px;');

        return abuser;
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
        dragEvents: 0
    },

    resetGameState() {
        this.gameState = {
            started: false,
            startTime: 0,
            interactions: 0,
            dragEvents: 0
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

    // 점수 검증
    validateScore(score, game, maxScore) {
        const validations = [];

        // 1. 불가능한 점수 체크
        if (score > maxScore) {
            validations.push({
                valid: false,
                reason: 'impossible_score',
                detail: `Score ${score} exceeds max ${maxScore}`
            });
        }

        // 2. 음수 점수 체크
        if (score < 0) {
            validations.push({
                valid: false,
                reason: 'impossible_score',
                detail: `Negative score: ${score}`
            });
        }

        // 3. 게임 시작 여부 체크
        if (!this.gameState.started) {
            validations.push({
                valid: false,
                reason: 'no_gameplay',
                detail: 'Score submitted without starting game'
            });
        }

        // 4. 게임 플레이 시간 체크 (최소 2초)
        const playTime = Date.now() - this.gameState.startTime;
        if (playTime < 2000 && this.gameState.started) {
            validations.push({
                valid: false,
                reason: 'speed_hack',
                detail: `Game completed in ${playTime}ms`
            });
        }

        // 5. 인터랙션 체크 (최소 1회)
        if (this.gameState.interactions < 1 && this.gameState.started) {
            validations.push({
                valid: false,
                reason: 'no_gameplay',
                detail: 'No interactions recorded'
            });
        }

        return validations.length === 0 ? { valid: true } : validations[0];
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
