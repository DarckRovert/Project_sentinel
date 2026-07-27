/* ============================================================
   SENTINEL ADAS - INTERACTIVE LIVE SIMULATOR LOGIC
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

    // DOM Elements
    const speedRange = document.getElementById('speedRange');
    const distanceRange = document.getElementById('distanceRange');
    const relSpeedRange = document.getElementById('relSpeedRange');

    const speedVal = document.getElementById('speedVal');
    const distanceVal = document.getElementById('distanceVal');
    const relSpeedVal = document.getElementById('relSpeedVal');

    // HUD Elements
    const simHudScreen = document.getElementById('sim-hud-screen');
    const simHudIcon = document.getElementById('sim-hud-icon');
    const simTtcVal = document.getElementById('sim-ttc-val');
    const simTtcLbl = document.getElementById('sim-ttc-lbl');
    const simThreatBadge = document.getElementById('sim-threat-badge');
    const simStatusPill = document.getElementById('sim-status-pill');

    // Haptic Seat Elements
    const nodeFront = document.getElementById('node-front');
    const nodeBack = document.getElementById('node-back');
    const nodeLeft = document.getElementById('node-left');
    const nodeRight = document.getElementById('node-right');

    const blePayloadEl = document.getElementById('ble-payload');
    const hapticIntensityEl = document.getElementById('haptic-intensity');
    const hapticPatternEl = document.getElementById('haptic-pattern');
    const hapticLatencyEl = document.getElementById('haptic-latency');

    // State
    let activeZone = 'FRONT'; // FRONT, BACK, LEFT, RIGHT

    // Event Listeners for Sliders
    speedRange.addEventListener('input', (e) => {
        speedVal.textContent = e.target.value;
        recalculateSimulation();
    });

    distanceRange.addEventListener('input', (e) => {
        distanceVal.textContent = parseFloat(e.target.value).toFixed(1);
        recalculateSimulation();
    });

    relSpeedRange.addEventListener('input', (e) => {
        relSpeedVal.textContent = parseFloat(e.target.value).toFixed(1);
        recalculateSimulation();
    });

    // Threat Zone Selector Function (Global scope for inline onclick)
    window.triggerZone = function(zone) {
        activeZone = zone;

        // Reset zone button styles
        document.querySelectorAll('.zone-btn').forEach(btn => btn.classList.remove('active'));

        const targetBtn = document.getElementById(`btn-${zone.toLowerCase()}`);
        if (targetBtn) {
            targetBtn.classList.add('active');
        }

        recalculateSimulation();
    };

    /**
     * Core Algorithm recalculation (matches Kotlin CollisionDetectionUseCase)
     */
    function recalculateSimulation() {
        const distanceM = parseFloat(distanceRange.value);
        const relSpeedMps = parseFloat(relSpeedRange.value); // negative = approaching

        let ttcSeconds = Infinity;
        let threatLevel = 'NONE'; // NONE, CAUTION, WARNING, CRITICAL

        // TTC = -distance / relative_velocity
        if (relSpeedMps < -0.1) {
            ttcSeconds = -distanceM / relSpeedMps;
        }

        // Classify Threat Level based on Sentinel thresholds
        if (ttcSeconds <= 2.0) {
            threatLevel = 'CRITICAL';
        } else if (ttcSeconds <= 4.0) {
            threatLevel = 'WARNING';
        } else if (ttcSeconds <= 6.0) {
            threatLevel = 'CAUTION';
        } else {
            threatLevel = 'NONE';
        }

        // Update HUD Visuals
        updateHudUI(threatLevel, ttcSeconds);

        // Update BLE GATT Payload & Seat Actuators
        updateHapticSeat(threatLevel, activeZone);
    }

    /**
     * Updates simulated Jetpack Compose HUD Screen
     */
    function updateHudUI(level, ttc) {
        simThreatBadge.textContent = level;

        // Reset border colors
        simHudScreen.style.borderColor = 'var(--accent-primary)';

        if (level === 'CRITICAL') {
            simHudIcon.textContent = '⚠️';
            simTtcVal.textContent = ttc.toFixed(1) + 's';
            simTtcVal.style.color = 'var(--color-critical)';
            simTtcLbl.textContent = 'IMPACTO EN CURSO';
            simHudScreen.style.borderColor = 'var(--color-critical)';
            simHudScreen.style.boxShadow = '0 0 30px rgba(255, 59, 48, 0.4)';
            simStatusPill.textContent = '🔴 ALERTA MÁXIMA';
            simStatusPill.style.background = 'rgba(255, 59, 48, 0.2)';
            simStatusPill.style.color = 'var(--color-critical)';
        } else if (level === 'WARNING') {
            simHudIcon.textContent = '🔶';
            simTtcVal.textContent = ttc.toFixed(1) + 's';
            simTtcVal.style.color = 'var(--color-warning)';
            simTtcLbl.textContent = 'RIESGO DE COLISIÓN';
            simHudScreen.style.borderColor = 'var(--color-warning)';
            simHudScreen.style.boxShadow = '0 0 20px rgba(255, 184, 0, 0.3)';
            simStatusPill.textContent = '🟡 ADVERTENCIA';
            simStatusPill.style.background = 'rgba(255, 184, 0, 0.2)';
            simStatusPill.style.color = 'var(--color-warning)';
        } else if (level === 'CAUTION') {
            simHudIcon.textContent = '🔵';
            simTtcVal.textContent = ttc.toFixed(1) + 's';
            simTtcVal.style.color = 'var(--color-caution)';
            simTtcLbl.textContent = 'OBJETO CERCANO';
            simHudScreen.style.borderColor = 'var(--color-caution)';
            simHudScreen.style.boxShadow = '0 0 15px rgba(58, 158, 255, 0.2)';
            simStatusPill.textContent = '🔵 PRECAUCIÓN';
            simStatusPill.style.background = 'rgba(58, 158, 255, 0.2)';
            simStatusPill.style.color = 'var(--color-caution)';
        } else {
            simHudIcon.textContent = '✅';
            simTtcVal.textContent = '--';
            simTtcVal.style.color = 'var(--accent-primary)';
            simTtcLbl.textContent = 'MONITOREANDO';
            simHudScreen.style.boxShadow = 'none';
            simStatusPill.textContent = 'MONITOREANDO';
            simStatusPill.style.background = 'rgba(0, 229, 160, 0.1)';
            simStatusPill.style.color = 'var(--accent-primary)';
        }
    }

    /**
     * Encodes BLE GATT 1-byte command & triggers seat vibration animations
     */
    function updateHapticSeat(level, zone) {
        // Reset all seat nodes
        [nodeFront, nodeBack, nodeLeft, nodeRight].forEach(node => {
            if (node) node.classList.remove('vibrating');
        });

        if (level === 'NONE') {
            blePayloadEl.textContent = '0x00';
            hapticIntensityEl.textContent = 'OFF (0%)';
            hapticPatternEl.textContent = 'NONE';
            hapticLatencyEl.textContent = '-- ms';
            return;
        }

        // Intensity (Bits 7:6)
        let intensityBits = 0b00;
        let intensityStr = 'OFF';
        if (level === 'CRITICAL') { intensityBits = 0b11; intensityStr = 'HIGH (100%)'; }
        else if (level === 'WARNING') { intensityBits = 0b10; intensityStr = 'MED (75%)'; }
        else if (level === 'CAUTION') { intensityBits = 0b01; intensityStr = 'LOW (33%)'; }

        // Zone (Bits 5:4)
        let zoneBits = 0b00;
        if (zone === 'FRONT') zoneBits = 0b00;
        else if (zone === 'BACK') zoneBits = 0b01;
        else if (zone === 'LEFT') zoneBits = 0b10;
        else if (zone === 'RIGHT') zoneBits = 0b11;

        // Pattern (Bits 3:0)
        let patternBits = 0b0000;
        let patternStr = 'Pulso Único';
        if (level === 'CRITICAL') { patternBits = 0b0010; patternStr = 'Continuo LRA'; }
        else if (level === 'WARNING') { patternBits = 0b0001; patternStr = 'Doble Pulso'; }

        // Calculate 1-byte payload
        const payloadByte = (intensityBits << 6) | (zoneBits << 4) | patternBits;
        const hexStr = '0x' + payloadByte.toString(16).padStart(2, '0').toUpperCase();

        blePayloadEl.textContent = hexStr;
        hapticIntensityEl.textContent = intensityStr;
        hapticPatternEl.textContent = patternStr;
        hapticLatencyEl.textContent = '18 ms (BLE 5.0)';

        // Activate corresponding seat node vibration UI
        if (zone === 'FRONT' && nodeFront) nodeFront.classList.add('vibrating');
        if (zone === 'BACK' && nodeBack) nodeBack.classList.add('vibrating');
        if (zone === 'LEFT' && nodeLeft) nodeLeft.classList.add('vibrating');
        if (zone === 'RIGHT' && nodeRight) nodeRight.classList.add('vibrating');
    }

    // Initialize default state on load
    triggerZone('FRONT');
});
