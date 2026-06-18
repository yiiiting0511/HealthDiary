function renderGainBars(gains) {
    const entries = Object.entries(gains);
    const maxGain = Math.max(...entries.map(([, v]) => v), 0.0001);
    const bestFeature = entries.reduce((a, b) => (b[1] > a[1] ? b : a))[0];

    const container = document.getElementById("gainBars");
    container.innerHTML = entries.map(([feature, gain]) => {
        const pct = (gain / maxGain) * 100;
        const isBest = feature === bestFeature;
        return `
            <div class="gain-bar-row">
                <div>${feature}${isBest ? " ★" : ""}</div>
                <div class="gain-bar-track"><div class="gain-bar-fill ${isBest ? "best" : ""}" style="width:${pct}%"></div></div>
                <div>${gain.toFixed(4)}</div>
            </div>
        `;
    }).join("");
}

function renderNode(node) {
    if (node.leaf) {
        const counts = Object.entries(node.classCounts).map(([k, v]) => `${k}:${v}`).join(", ");
        return `
            <li class="tree-node">
                <div class="node-box leaf">葉節點 → ${node.predictedLabel}<br>
                <span class="muted">entropy=${node.entropy} | n=${node.sampleCount} | ${counts}</span></div>
            </li>
        `;
    }
    return `
        <li class="tree-node">
            <div class="node-box">${node.feature} ≤ ${node.threshold} ?
            <br><span class="muted">entropy=${node.entropy} | n=${node.sampleCount}</span></div>
            <ul>
                <li class="muted" style="margin-bottom:4px;">是 (≤ ${node.threshold})</li>
                ${renderNode(node.left)}
                <li class="muted" style="margin: 8px 0 4px;">否 (&gt; ${node.threshold})</li>
                ${renderNode(node.right)}
            </ul>
        </li>
    `;
}

async function loadModel() {
    try {
        const tree = await api.get("/api/model/tree");
        const meta = document.getElementById("treeMeta");
        const entropyEl = document.getElementById("entropyValue");
        const treeView = document.getElementById("treeView");

        if (!tree.trained) {
            meta.textContent = "尚未訓練模型，請先到設定頁新增種子訓練資料。";
            entropyEl.textContent = "-";
            document.getElementById("gainBars").innerHTML = "";
            treeView.innerHTML = '<span class="muted">無資料</span>';
            return;
        }

        meta.textContent = `訓練樣本數：${tree.sampleCount} ｜ 根節點特徵：${tree.rootFeature} ｜ 根節點門檻：${tree.rootThreshold}`;
        entropyEl.textContent = `H(S) = ${tree.entropy.toFixed(4)}`;
        renderGainBars(tree.informationGain);
        treeView.innerHTML = `<ul>${renderNode(tree.tree)}</ul>`;
    } catch (e) {
        showToast("載入決策樹資訊失敗：" + e.message);
    }
}

function initModelPage() {
    document.getElementById("retrainBtn").addEventListener("click", async () => {
        try {
            await api.post("/api/model/train", {});
            showToast("模型已重新訓練");
            loadModel();
        } catch (e) {
            showToast("訓練失敗：" + e.message);
        }
    });
    loadModel();
}

document.addEventListener("DOMContentLoaded", initModelPage);
