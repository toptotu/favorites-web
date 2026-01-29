$(function() {
    $("#go-audit-scan").on("click", function() {
        runGoAuditScan();
    });
    $("#go-audit-reset").on("click", function() {
        resetGoAudit();
    });
    $("#go-audit-results").on("click", ".go-audit-toggle", function() {
        var targetId = $(this).data("target");
        $("#" + targetId).toggle();
        var text = $(this).text() === "展开详情" ? "收起详情" : "展开详情";
        $(this).text(text);
    });
});

function runGoAuditScan() {
    var target = $.trim($("#go-audit-target").val());
    if (target === "") {
        showGoAuditStatus("请输入本地路径或仓库地址。", true);
        return;
    }
    var sourceType = $("input[name='sourceType']:checked").val();
    var branch = $.trim($("#go-audit-branch").val());
    var payload = {
        target: target,
        sourceType: sourceType,
        branch: branch
    };
    setGoAuditLoading(true);
    $.ajax({
        type: "POST",
        url: "/api/go-audit/scan",
        data: JSON.stringify(payload),
        contentType: "application/json",
        dataType: "json",
        success: function(response) {
            handleGoAuditResponse(response);
        },
        error: function(xhr) {
            showGoAuditStatus("扫描失败，请稍后重试。", true);
            console.log(xhr);
        },
        complete: function() {
            setGoAuditLoading(false);
        }
    });
}

function resetGoAudit() {
    $("#go-audit-target").val("");
    $("#go-audit-branch").val("");
    $("#go-audit-results").empty();
    $("#go-audit-summary").empty();
    $("#go-audit-status").text("");
}

function handleGoAuditResponse(response) {
    if (!response || response.rspCode !== "000000") {
        var message = response && response.rspMsg ? response.rspMsg : "扫描失败。";
        showGoAuditStatus(message, true);
        return;
    }
    var data = response.data;
    renderGoAuditSummary(data);
    renderGoAuditIssues(data ? data.issues : []);
    showGoAuditStatus("扫描完成。", false);
}

function renderGoAuditSummary(data) {
    if (!data) {
        $("#go-audit-summary").html("");
        return;
    }
    var summary = "扫描文件 " + data.filesScanned + " 个，发现问题 " + data.issuesFound + " 个";
    if (data.skippedFiles && data.skippedFiles > 0) {
        summary += "，跳过大文件 " + data.skippedFiles + " 个";
    }
    $("#go-audit-summary").html("<span class='text-info'>" + escapeHtml(summary) + "</span>");
}

function renderGoAuditIssues(issues) {
    var container = $("#go-audit-results");
    container.empty();
    if (!issues || issues.length === 0) {
        container.append("<div class='alert alert-info'>未发现可疑问题单。</div>");
        return;
    }
    for (var i = 0; i < issues.length; i++) {
        var issue = issues[i];
        var detailId = "go-audit-detail-" + i;
        var severityLabel = getSeverityLabel(issue.severity);
        var header = "<div class='panel-heading'>" +
            "<span class='label " + severityLabel + "'>" + escapeHtml(issue.severity || "LOW") + "</span> " +
            "<strong class='ml'>" + escapeHtml(issue.title || "风险问题") + "</strong>" +
            "<span class='text-muted pull-right'>" + escapeHtml(issue.filePath || "") +
            ":" + (issue.lineStart || 0) + "</span>" +
            "</div>";
        var body = "<div class='panel-body'>" +
            "<p><strong>问题描述：</strong>" + escapeHtml(issue.summary || "") + "</p>" +
            "<p><strong>风险影响：</strong>" + escapeHtml(issue.risk || "") + "</p>" +
            "<p><strong>修复建议：</strong>" + escapeHtml(issue.recommendation || "") + "</p>" +
            "<a href='javascript:void(0);' class='go-audit-toggle' data-target='" + detailId + "'>展开详情</a>" +
            "<div id='" + detailId + "' style='display:none;margin-top:10px;'>" +
            "<p><strong>污点来源：</strong>" + escapeHtml(issue.source || "") + "</p>" +
            "<p><strong>风险汇聚：</strong>" + escapeHtml(issue.sink || "") + "</p>" +
            "<p><strong>代码切片：</strong></p>" +
            "<pre style='white-space: pre-wrap;'>" + escapeHtml(formatSlice(issue.slice)) + "</pre>" +
            "<p class='text-muted'>提示词</p>" +
            "<pre style='white-space: pre-wrap;'>" + escapeHtml(issue.modelPrompt || "") + "</pre>" +
            "</div>" +
            "</div>";
        container.append("<div class='panel panel-default'>" + header + body + "</div>");
    }
}

function formatSlice(slice) {
    if (!slice || slice.length === 0) {
        return "";
    }
    var lines = [];
    for (var i = 0; i < slice.length; i++) {
        var line = slice[i];
        lines.push("L" + line.line + "[" + line.kind + "] " + line.code);
    }
    return lines.join("\n");
}

function setGoAuditLoading(isLoading) {
    $("#go-audit-scan").prop("disabled", isLoading);
    $("#go-audit-reset").prop("disabled", isLoading);
    if (isLoading) {
        $("#go-audit-status").text("扫描中，请稍候...");
    }
}

function showGoAuditStatus(message, isError) {
    var labelClass = isError ? "text-danger" : "text-success";
    $("#go-audit-status").html("<span class='" + labelClass + "'>" + escapeHtml(message) + "</span>");
}

function getSeverityLabel(severity) {
    if (severity === "HIGH") {
        return "label-danger";
    }
    if (severity === "MEDIUM") {
        return "label-warning";
    }
    return "label-info";
}

function escapeHtml(value) {
    if (value === null || value === undefined) {
        return "";
    }
    return value.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}
