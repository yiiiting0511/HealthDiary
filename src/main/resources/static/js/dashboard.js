async function loadDashboard() {
    try {
        const logs = await api.get("/api/logs?sort=desc");
        const latestRiskEl = document.getElementById("latestRisk");
        const body = document.getElementById("recentLogsBody");

        if (logs.length === 0) {
            latestRiskEl.innerHTML = '<span class="muted">尚無紀錄，請先新增今日健康資料。</span>';
            body.innerHTML = '<tr><td colspan="5" class="muted">尚無紀錄</td></tr>';
        } else {
            const latest = logs[0];
            latestRiskEl.innerHTML =
                `${riskBadgeHtml(latest.riskLevel)} <span class="muted">最後更新：${latest.logDate}</span>`;
            document.getElementById("statSleep").textContent = `${latest.sleepHours} 小時`;
            document.getElementById("statSteps").textContent = `${latest.steps} 步`;
            document.getElementById("statMood").textContent = `${latest.moodScore} / 10`;

            body.innerHTML = logs.slice(0, 7).map((log) => `
                <tr>
                    <td>${log.logDate}</td>
                    <td>${log.sleepHours}</td>
                    <td>${log.steps}</td>
                    <td>${log.moodScore}</td>
                    <td>${riskBadgeHtml(log.riskLevel)}</td>
                </tr>
            `).join("");
        }
    } catch (e) {
        showToast("載入儀表板資料失敗：" + e.message);
    }

    try {
        const tree = await api.get("/api/model/tree");
        const summaryEl = document.getElementById("modelSummary");
        if (!tree.trained) {
            summaryEl.textContent = "模型尚未訓練，請至設定頁新增種子資料並訓練。";
        } else {
            summaryEl.innerHTML =
                `根節點特徵：<b>${tree.rootFeature}</b>（門檻 ${tree.rootThreshold}）` +
                ` ｜ 整體 Entropy：${tree.entropy.toFixed(4)}` +
                ` ｜ 訓練樣本數：${tree.sampleCount}`;
        }
    } catch (e) {
        document.getElementById("modelSummary").textContent = "載入模型資訊失敗：" + e.message;
    }
}

document.addEventListener("DOMContentLoaded", loadDashboard);
