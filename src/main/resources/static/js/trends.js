function lineChart(canvasId, labels, data, label, color) {
    const ctx = document.getElementById(canvasId).getContext("2d");
    return new Chart(ctx, {
        type: "line",
        data: {
            labels,
            datasets: [{
                label,
                data,
                borderColor: color,
                backgroundColor: color + "33",
                tension: 0.25,
                fill: true,
                pointRadius: 3,
            }],
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: false } },
        },
    });
}

async function loadTrends() {
    // Chart.js is now loaded from a local file (../vendor/chart.umd.js) instead of a
    // public CDN, so this page no longer depends on the browser being able to reach
    // cdnjs.cloudflare.com. If this still fails, it means the local <script> tag
    // itself didn't load (e.g. wrong path) rather than a network/firewall issue.
    if (typeof Chart === "undefined") {
        const msg = "Chart.js 函式庫未載入成功（../vendor/chart.umd.js），請確認該檔案存在。";
        console.error(msg);
        document.querySelectorAll("main .card").forEach((card) => {
            card.innerHTML = `<p class="muted">${msg}</p>`;
        });
        return;
    }

    try {
        // Use the history endpoint, which excludes seed/training rows so the
        // trend charts only reflect the user's own manually-entered logs.
        const logs = await api.get("/api/logs?sort=asc");

        if (!logs || logs.length === 0) {
            document.querySelectorAll("main .card").forEach((card) => {
                card.innerHTML = '<p class="muted">尚無資料可顯示趨勢，請先到「健康資料輸入」新增紀錄。</p>';
            });
            return;
        }

        const labels = logs.map((l) => l.logDate);
        lineChart("sleepChart", labels, logs.map((l) => l.sleepHours), "睡眠時數", "#2563eb");
        lineChart("stepsChart", labels, logs.map((l) => l.steps), "步數", "#16a34a");
        lineChart("moodChart", labels, logs.map((l) => l.moodScore), "心情分數", "#ca8a04");
    } catch (e) {
        console.error("loadTrends failed:", e);
        showToast("載入趨勢資料失敗：" + e.message);
        document.querySelectorAll("main .card").forEach((card) => {
            const p = document.createElement("p");
            p.className = "muted";
            p.textContent = "載入趨勢資料失敗：" + e.message;
            card.appendChild(p);
        });
    }
}

document.addEventListener("DOMContentLoaded", loadTrends);
