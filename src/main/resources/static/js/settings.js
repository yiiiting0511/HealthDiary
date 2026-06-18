async function loadLogs() {
    try {
        const logs = await api.get("/api/logs?sort=desc");
        const body = document.getElementById("logsBody");
        if (logs.length === 0) {
            body.innerHTML = '<tr><td colspan="6" class="muted">尚無紀錄</td></tr>';
            return;
        }
        body.innerHTML = logs.map((log) => `
            <tr>
                <td>${log.logDate}</td>
                <td>${log.sleepHours}</td>
                <td>${log.steps}</td>
                <td>${log.moodScore}</td>
                <td>${riskBadgeHtml(log.riskLevel)}</td>
                <td>
                    <button class="secondary" onclick="startEdit(${log.id}, '${log.logDate}', ${log.sleepHours}, ${log.steps}, ${log.moodScore})">編輯</button>
                    <button class="danger" onclick="deleteLog(${log.id})">刪除</button>
                </td>
            </tr>
        `).join("");
    } catch (e) {
        showToast("載入歷史紀錄失敗：" + e.message);
    }
}

function startEdit(id, logDate, sleepHours, steps, moodScore) {
    document.getElementById("editCard").style.display = "block";
    document.getElementById("editId").value = id;
    document.getElementById("editLogDate").value = logDate;
    document.getElementById("editSleepHours").value = sleepHours;
    document.getElementById("editSteps").value = steps;
    document.getElementById("editMoodScore").value = moodScore;
    window.scrollTo({ top: 0, behavior: "smooth" });
}

async function deleteLog(id) {
    if (!confirm("確定要刪除這筆紀錄嗎？")) return;
    try {
        await api.del(`/api/logs/${id}`);
        showToast("已刪除");
        loadLogs();
    } catch (e) {
        showToast("刪除失敗：" + e.message);
    }
}

async function loadSeedData() {
    try {
        const seeds = await api.get("/api/model/seed");
        const body = document.getElementById("seedBody");
        if (seeds.length === 0) {
            body.innerHTML = '<tr><td colspan="6" class="muted">尚無種子資料</td></tr>';
            return;
        }
        body.innerHTML = seeds.map((s) => `
            <tr>
                <td>${s.logDate}</td>
                <td>${s.sleepHours}</td>
                <td>${s.steps}</td>
                <td>${s.moodScore}</td>
                <td>${riskBadgeHtml(s.riskLevel)}</td>
                <td><button class="danger" onclick="deleteSeed(${s.id})">刪除</button></td>
            </tr>
        `).join("");
    } catch (e) {
        showToast("載入種子資料失敗：" + e.message);
    }
}

async function deleteSeed(id) {
    if (!confirm("確定要刪除這筆種子資料嗎？刪除後模型會自動重新訓練。")) return;
    try {
        await api.del(`/api/model/seed/${id}`);
        showToast("已刪除並重新訓練模型");
        loadSeedData();
    } catch (e) {
        showToast("刪除失敗：" + e.message);
    }
}

function initSettingsPage() {
    document.getElementById("seedLogDate").value = new Date().toISOString().slice(0, 10);

    document.getElementById("editForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        const id = document.getElementById("editId").value;
        const payload = {
            logDate: document.getElementById("editLogDate").value,
            sleepHours: parseFloat(document.getElementById("editSleepHours").value),
            steps: parseInt(document.getElementById("editSteps").value, 10),
            moodScore: parseInt(document.getElementById("editMoodScore").value, 10),
        };
        try {
            await api.put(`/api/logs/${id}`, payload);
            showToast("已更新");
            document.getElementById("editCard").style.display = "none";
            loadLogs();
        } catch (err) {
            showToast("更新失敗：" + err.message);
        }
    });

    document.getElementById("cancelEdit").addEventListener("click", () => {
        document.getElementById("editCard").style.display = "none";
    });

    document.getElementById("seedForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            logDate: document.getElementById("seedLogDate").value,
            sleepHours: parseFloat(document.getElementById("seedSleepHours").value),
            steps: parseInt(document.getElementById("seedSteps").value, 10),
            moodScore: parseInt(document.getElementById("seedMoodScore").value, 10),
            riskLevel: document.getElementById("seedRiskLevel").value,
        };
        try {
            await api.post("/api/model/seed", payload);
            showToast("已新增種子資料並重新訓練模型");
            document.getElementById("seedForm").reset();
            document.getElementById("seedLogDate").value = new Date().toISOString().slice(0, 10);
            loadSeedData();
        } catch (err) {
            showToast("新增失敗：" + err.message);
        }
    });

    loadLogs();
    loadSeedData();
}

document.addEventListener("DOMContentLoaded", initSettingsPage);
