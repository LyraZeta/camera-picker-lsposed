(function () {
    "use strict";

    const state = {
        objectUrl: null,
        eventCount: 0,
        targetId: ""
    };

    const elements = {
        protocolStatus: document.getElementById("protocol-status"),
        securityStatus: document.getElementById("security-status"),
        pageUrl: document.getElementById("page-url"),
        browserName: document.getElementById("browser-name"),
        resultState: document.getElementById("result-state"),
        previewStage: document.getElementById("preview-stage"),
        imagePreview: document.getElementById("image-preview"),
        canvas: document.getElementById("test-target"),
        clearButton: document.getElementById("clear-button"),
        regenerateTarget: document.getElementById("regenerate-target"),
        downloadTarget: document.getElementById("download-target"),
        eventList: document.getElementById("event-list"),
        eventCount: document.getElementById("event-count"),
        metaMode: document.getElementById("meta-mode"),
        metaName: document.getElementById("meta-name"),
        metaType: document.getElementById("meta-type"),
        metaSize: document.getElementById("meta-size"),
        metaDimensions: document.getElementById("meta-dimensions"),
        metaModified: document.getElementById("meta-modified"),
        metaHash: document.getElementById("meta-hash")
    };

    function initialize() {
        renderEnvironment();
        drawTestTarget();
        bindInputs();
        elements.clearButton.addEventListener("click", clearResult);
        elements.regenerateTarget.addEventListener("click", function () {
            drawTestTarget();
            clearResult();
            addEvent("已重新生成测试样张 " + state.targetId);
        });
        elements.downloadTarget.addEventListener("click", downloadTestTarget);
        addEvent("页面已加载，等待浏览器文件输入");
    }

    function renderEnvironment() {
        const protocol = window.location.protocol.replace(":", "").toUpperCase() || "FILE";
        elements.protocolStatus.textContent = protocol;
        elements.protocolStatus.classList.add(protocol === "HTTPS" ? "status-ok" : "status-warn");

        elements.securityStatus.textContent = window.isSecureContext ? "安全上下文" : "普通上下文";
        elements.securityStatus.classList.add(window.isSecureContext ? "status-ok" : "status-warn");
        elements.pageUrl.textContent = window.location.href;
        elements.browserName.textContent = compactUserAgent(navigator.userAgent);
    }

    function bindInputs() {
        document.querySelectorAll("input[type='file']").forEach(function (input) {
            input.addEventListener("click", function () {
                addEvent("触发：" + input.dataset.mode);
            });
            input.addEventListener("change", function () {
                const file = input.files && input.files[0];
                if (!file) {
                    addEvent("取消：" + input.dataset.mode);
                    setResultState("未选择文件", false);
                    return;
                }
                displayFile(file, input.dataset.mode);
            });
        });
    }

    async function displayFile(file, mode) {
        revokeObjectUrl();
        state.objectUrl = URL.createObjectURL(file);
        elements.imagePreview.src = state.objectUrl;
        elements.previewStage.classList.add("has-image");
        elements.metaMode.textContent = mode;
        elements.metaName.textContent = file.name || "(未提供)";
        elements.metaType.textContent = file.type || "(未提供)";
        elements.metaSize.textContent = formatBytes(file.size);
        elements.metaModified.textContent = file.lastModified
            ? new Date(file.lastModified).toLocaleString()
            : "(未提供)";
        elements.metaDimensions.textContent = "读取中";
        elements.metaHash.textContent = "计算中";
        setResultState("已收到图片", true);
        addEvent("返回：" + mode + " / " + (file.name || "unnamed") + " / " + formatBytes(file.size));

        try {
            const dimensions = await readImageDimensions(state.objectUrl);
            elements.metaDimensions.textContent = dimensions.width + " x " + dimensions.height;
            addEvent("解析尺寸：" + dimensions.width + " x " + dimensions.height);
        } catch (error) {
            elements.metaDimensions.textContent = "无法解析";
            addEvent("尺寸解析失败");
        }

        try {
            elements.metaHash.textContent = await hashFile(file);
        } catch (error) {
            elements.metaHash.textContent = window.isSecureContext ? "计算失败" : "需要安全上下文";
        }
    }

    function clearResult() {
        revokeObjectUrl();
        elements.previewStage.classList.remove("has-image");
        elements.imagePreview.removeAttribute("src");
        document.querySelectorAll("input[type='file']").forEach(function (input) {
            input.value = "";
        });
        [
            elements.metaMode,
            elements.metaName,
            elements.metaType,
            elements.metaSize,
            elements.metaDimensions,
            elements.metaModified,
            elements.metaHash
        ].forEach(function (element) {
            element.textContent = "--";
        });
        setResultState("等待触发", false);
        addEvent("结果已清空");
    }

    function setResultState(message, success) {
        elements.resultState.textContent = message;
        elements.resultState.classList.remove("status-ok", "status-warn");
        elements.resultState.classList.add(success ? "status-ok" : "status-warn");
    }

    function drawTestTarget() {
        const canvas = elements.canvas;
        const context = canvas.getContext("2d");
        const width = canvas.width;
        const height = canvas.height;
        const now = new Date();
        state.targetId = createTargetId(now);

        context.fillStyle = "#f5f7fa";
        context.fillRect(0, 0, width, height);

        const bars = ["#1769aa", "#d8b33f", "#247451", "#c64f3d", "#6c4f8b", "#20252b"];
        bars.forEach(function (color, index) {
            context.fillStyle = color;
            context.fillRect(index * width / bars.length, 0, width / bars.length + 1, 140);
        });

        context.fillStyle = "#171a1f";
        context.font = "700 54px Segoe UI, sans-serif";
        context.fillText("CAMERA PICKER TEST", 70, 245);

        context.fillStyle = "#5b626b";
        context.font = "32px Cascadia Mono, Consolas, monospace";
        context.fillText(state.targetId, 72, 302);
        context.fillText(width + " x " + height, 72, 352);

        drawCheckerboard(context, 70, 420, 420, 340, 10);
        drawFocusTarget(context, 760, 580, 185);

        context.fillStyle = "#171a1f";
        context.font = "26px Cascadia Mono, Consolas, monospace";
        context.fillText(now.toISOString(), 630, 830);
    }

    function drawCheckerboard(context, x, y, width, height, cells) {
        const cellWidth = width / cells;
        const cellHeight = height / Math.round(cells * height / width);
        const rows = Math.round(height / cellHeight);
        for (let row = 0; row < rows; row += 1) {
            for (let column = 0; column < cells; column += 1) {
                context.fillStyle = (row + column) % 2 === 0 ? "#15191e" : "#ffffff";
                context.fillRect(
                    x + column * cellWidth,
                    y + row * cellHeight,
                    cellWidth + 1,
                    cellHeight + 1
                );
            }
        }
        context.strokeStyle = "#15191e";
        context.lineWidth = 4;
        context.strokeRect(x, y, width, height);
    }

    function drawFocusTarget(context, centerX, centerY, radius) {
        context.save();
        context.translate(centerX, centerY);
        for (let ring = 8; ring >= 1; ring -= 1) {
            context.beginPath();
            context.arc(0, 0, radius * ring / 8, 0, Math.PI * 2);
            context.fillStyle = ring % 2 === 0 ? "#ffffff" : "#171a1f";
            context.fill();
        }
        context.strokeStyle = "#c64f3d";
        context.lineWidth = 5;
        context.beginPath();
        context.moveTo(-radius - 38, 0);
        context.lineTo(radius + 38, 0);
        context.moveTo(0, -radius - 38);
        context.lineTo(0, radius + 38);
        context.stroke();
        context.restore();
    }

    function downloadTestTarget() {
        const link = document.createElement("a");
        link.download = "camera-picker-test-" + state.targetId + ".png";
        link.href = elements.canvas.toDataURL("image/png");
        link.click();
        addEvent("已请求下载测试样张 " + state.targetId);
    }

    function readImageDimensions(url) {
        return new Promise(function (resolve, reject) {
            const image = new Image();
            image.onload = function () {
                resolve({width: image.naturalWidth, height: image.naturalHeight});
            };
            image.onerror = reject;
            image.src = url;
        });
    }

    async function hashFile(file) {
        if (!window.crypto || !window.crypto.subtle) {
            throw new Error("SubtleCrypto unavailable");
        }
        const digest = await window.crypto.subtle.digest("SHA-256", await file.arrayBuffer());
        return Array.from(new Uint8Array(digest))
            .map(function (byte) {
                return byte.toString(16).padStart(2, "0");
            })
            .join("");
    }

    function addEvent(message) {
        state.eventCount += 1;
        const item = document.createElement("li");
        const time = document.createElement("span");
        const text = document.createElement("span");
        time.className = "event-time";
        time.textContent = new Date().toLocaleTimeString();
        text.textContent = message;
        item.append(time, text);
        elements.eventList.prepend(item);
        elements.eventCount.textContent = String(state.eventCount);

        while (elements.eventList.children.length > 20) {
            elements.eventList.lastElementChild.remove();
        }
    }

    function revokeObjectUrl() {
        if (state.objectUrl) {
            URL.revokeObjectURL(state.objectUrl);
            state.objectUrl = null;
        }
    }

    function formatBytes(bytes) {
        if (!Number.isFinite(bytes) || bytes < 0) {
            return "--";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        const units = ["KB", "MB", "GB"];
        let value = bytes / 1024;
        let unit = units[0];
        for (let index = 1; index < units.length && value >= 1024; index += 1) {
            value /= 1024;
            unit = units[index];
        }
        return value.toFixed(value >= 10 ? 1 : 2) + " " + unit;
    }

    function compactUserAgent(userAgent) {
        const source = userAgent || "unknown";
        const candidates = [
            /EdgA\/([\d.]+)/,
            /Chrome\/([\d.]+)/,
            /Firefox\/([\d.]+)/,
            /Version\/([\d.]+).*Safari/
        ];
        const labels = ["Edge Android", "Chrome", "Firefox", "Safari"];
        for (let index = 0; index < candidates.length; index += 1) {
            const match = source.match(candidates[index]);
            if (match) {
                return labels[index] + " " + match[1];
            }
        }
        return source.length > 96 ? source.slice(0, 93) + "..." : source;
    }

    function createTargetId(date) {
        const values = [
            date.getFullYear(),
            String(date.getMonth() + 1).padStart(2, "0"),
            String(date.getDate()).padStart(2, "0"),
            String(date.getHours()).padStart(2, "0"),
            String(date.getMinutes()).padStart(2, "0"),
            String(date.getSeconds()).padStart(2, "0")
        ];
        return values.slice(0, 3).join("") + "-" + values.slice(3).join("");
    }

    window.addEventListener("beforeunload", revokeObjectUrl);
    initialize();
}());
