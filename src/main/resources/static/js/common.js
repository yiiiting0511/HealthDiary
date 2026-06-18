// Shared helpers used by every page.

async function apiRequest(path, options = {}) {
    const res = await fetch(path, {
        headers: { "Content-Type": "application/json" },
        ...options,
    });
    if (!res.ok) {
        let message = `Request failed (${res.status})`;
        try {
            const body = await res.json();
            message = body.error || JSON.stringify(body);
        } catch (e) { /* ignore */ }
        throw new Error(message);
    }
    if (res.status === 204) return null;
    return res.json();
}

const api = {
    get: (path) => apiRequest(path),
    post: (path, body) => apiRequest(path, { method: "POST", body: JSON.stringify(body) }),
    put: (path, body) => apiRequest(path, { method: "PUT", body: JSON.stringify(body) }),
    del: (path) => apiRequest(path, { method: "DELETE" }),
};

function riskBadgeHtml(level) {
    const cls = level === "High" ? "risk-high" : level === "Medium" ? "risk-medium" : "risk-low";
    return `<span class="badge ${cls}">${level || "-"}</span>`;
}

function showToast(message) {
    let toast = document.getElementById("toast");
    if (!toast) {
        toast = document.createElement("div");
        toast.id = "toast";
        document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.classList.add("show");
    clearTimeout(toast._hideTimer);
    toast._hideTimer = setTimeout(() => toast.classList.remove("show"), 2500);
}

function highlightActiveNav() {
    const page = document.body.dataset.page;
    document.querySelectorAll("nav.app-nav a").forEach((a) => {
        if (a.dataset.page === page) a.classList.add("active");
    });
}

function formatDate(d) {
    if (!d) return "-";
    return d;
}

document.addEventListener("DOMContentLoaded", highlightActiveNav);
