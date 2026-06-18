function initEntryForm() {
    const dateInput = document.getElementById("logDate");
    dateInput.value = new Date().toISOString().slice(0, 10);

    document.getElementById("entryForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            logDate: document.getElementById("logDate").value,
            sleepHours: parseFloat(document.getElementById("sleepHours").value),
            steps: parseInt(document.getElementById("steps").value, 10),
            moodScore: parseInt(document.getElementById("moodScore").value, 10),
        };
        try {
            const saved = await api.post("/api/logs", payload);
            const resultCard = document.getElementById("resultCard");
            resultCard.style.display = "block";
            document.getElementById("resultRisk").innerHTML =
                `這筆紀錄的風險等級：${riskBadgeHtml(saved.riskLevel)}`;
            document.getElementById("resultInsight").textContent = "正在取得 AI 健康建議...";

            try {
                const insight = await api.get(`/api/ai/insight/${saved.id}`);
                document.getElementById("resultInsight").textContent = "AI 建議：" + insight.insight;
            } catch (err) {
                document.getElementById("resultInsight").textContent = "AI 建議目前無法取得。";
            }

            showToast("已儲存今日健康紀錄");
            document.getElementById("entryForm").reset();
            dateInput.value = new Date().toISOString().slice(0, 10);
        } catch (err) {
            showToast("送出失敗：" + err.message);
        }
    });
}

document.addEventListener("DOMContentLoaded", initEntryForm);
