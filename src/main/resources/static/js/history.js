let sortAsc = false;
let allLogs = [];

function renderHistory() {
    const from = document.getElementById("filterFrom").value;
    const to = document.getElementById("filterTo").value;

    let rows = allLogs;
    if (from) rows = rows.filter((r) => r.logDate >= from);
    if (to) rows = rows.filter((r) => r.logDate <= to);

    const body = document.getElementById("historyBody");
    if (rows.length === 0) {
        body.innerHTML = '<tr><td colspan="5" class="muted">沒有符合條件的紀錄</td></tr>';
        return;
    }
    body.innerHTML = rows.map((log) => `
        <tr>
            <td>${log.logDate}</td>
            <td>${log.sleepHours}</td>
            <td>${log.steps}</td>
            <td>${log.moodScore}</td>
            <td>${riskBadgeHtml(log.riskLevel)}</td>
        </tr>
    `).join("");
}

async function loadHistory() {
    try {
        allLogs = await api.get(`/api/logs?sort=${sortAsc ? "asc" : "desc"}`);
        renderHistory();
    } catch (e) {
        showToast("載入歷史紀錄失敗：" + e.message);
    }
}

function initHistoryPage() {
    document.getElementById("sortByDate").addEventListener("click", () => {
        sortAsc = !sortAsc;
        document.getElementById("sortArrow").textContent = sortAsc ? "↑" : "↓";
        loadHistory();
    });
    document.getElementById("applyFilter").addEventListener("click", renderHistory);
    document.getElementById("clearFilter").addEventListener("click", () => {
        document.getElementById("filterFrom").value = "";
        document.getElementById("filterTo").value = "";
        renderHistory();
    });
    loadHistory();
}

document.addEventListener("DOMContentLoaded", initHistoryPage);
