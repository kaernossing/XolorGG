// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

final class DiagnosticSummary {
    static String render(boolean zh, String connection, long age, String result, String target, String reason) {
        String state = zh ? "未知" : "Unknown";
        if ("CONNECTED".equals(connection)) state = zh ? "已连接" : "Connected";
        if ("DISCONNECTED".equals(connection)) state = zh ? "连接中断" : "Disconnected";
        String request;
        switch (result) {
            case "ACCEPTED": request = zh ? "请求已接受；未确认界面是否出现" : "Request accepted; UI appearance unverified"; break;
            case "FAILED": request = zh ? "请求失败" : "Request failed"; break;
            case "SCHEDULED": request = zh ? "等待发送" : "Scheduled"; break;
            case "BUSY_IGNORED": request = zh ? "已有调用正在处理，本次忽略" : "Ignored while another request was active"; break;
            case "CANCELLED": request = zh ? "服务停止，调用取消" : "Cancelled when service stopped"; break;
            case "UNAVAILABLE": request = zh ? "调用组件不可用" : "Activation unavailable"; break;
            default: request = zh ? "本次应用会话尚无记录" : "No request recorded in this app session";
        }
        String targetText = "ASSISTANT".equals(target) ? "Gemini/Assistant" : "CTS".equals(target) ? "Circle to Search" : "—";
        return (zh ? "Google 助手连接：" : "Google assistant connection: ") + state
                + "\n" + (zh ? "最近采样：" : "Last sample: ")
                + (age < 0 ? (zh ? "无" : "none") : age / 1000 + (zh ? " 秒前" : " s ago"))
                + (zh ? "（超过 30 秒显示未知）" : " (unknown after 30 s)")
                + "\n" + (zh ? "采样结果：" : "Sample result: ") + reason
                + "\n" + (zh ? "最近请求：" : "Last request: ") + targetText + " · " + request
                + "\n" + (zh ? "会话存在或提示音响起，不代表 Gemini 界面已出现。"
                : "A session or request sound does not confirm that Gemini appeared.");
    }
    private DiagnosticSummary() {}
}
