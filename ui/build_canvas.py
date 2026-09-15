#!/usr/bin/env python3
"""Build the Chengke (澄课) Miuix Canvas document and share hash."""

from __future__ import annotations

import base64
import json
import zlib
from pathlib import Path

OUT = Path("/home/lazybones/gzus/ui/gzus-timetable.miuix.json")
SHARE = Path("/home/lazybones/gzus/ui/share-url.txt")

GAP = 96
PHONE_W = 412
PHONE_H = 892


def item(kind: str, x: float, y: float, w: float, h: float, label: str, **extra):
    slot = extra.pop("slot", None)
    if slot is None:
        if kind == "topAppBar":
            slot = "topBar"
        elif kind in ("navigationBar", "floatingNav"):
            slot = "bottomBar"
        elif kind == "fab":
            slot = "floatingActionButton"
        elif kind == "snackbar":
            slot = "snackbarHost"
        elif kind in (
            "dialog",
            "bottomSheet",
            "listPopup",
            "cascadingPopup",
            "dropdownMenu",
            "iconDropdownMenu",
            "iconCascadingMenu",
        ):
            slot = "overlay"
        else:
            slot = "content"
    rec = {
        "id": extra.pop("id"),
        "kind": kind,
        "x": x,
        "y": y,
        "w": w,
        "h": h,
        "label": label,
        "enabled": True,
        "show": True,
        "slot": slot,
    }
    rec.update(extra)
    return rec


def glass_nav(selected: int, prefix: str, **tos):
    tabs = [
        {"icon": "home", "label": "今日", "to": tos.get("today", "today"), "transition": "fade"},
        {"icon": "date_range", "label": "课表", "to": tos.get("week", "week"), "transition": "fade"},
        {"icon": "school", "label": "成绩", "to": tos.get("grades", "grades"), "transition": "fade"},
        {"icon": "person", "label": "我的", "to": tos.get("me", "me"), "transition": "fade"},
    ]
    return item(
        "floatingNav",
        0,
        792,
        412,
        100,
        "悬浮导航",
        id=f"{prefix}-nav",
        variant="iosLike",
        effect="textureBlur",
        blurRadius=28,
        tabs=tabs,
        selected=selected,
    )


def screen(sid: str, name: str, index: int, items: list, note: str = "", swipe=None):
    rec = {
        "id": sid,
        "name": name,
        "x": index * (PHONE_W + GAP),
        "y": 0,
        "preset": "phone",
        "note": note,
        "items": items,
    }
    if swipe:
        rec["swipe"] = swipe
    return rec


doc = {
    "version": 3,
    "title": "澄课",
    "brief": (
        "澄课：广州商学院学生课表 App，名字取「把难看的正方课表澄清楚」。"
        "打开先进今日，不设登录墙。正方学号密码只出现在「我的」，登录后同步课表、成绩、考试。"
        "底栏用 FloatingNavigationBar，variant=iosLike，Modifier.textureBlur 液态玻璃悬浮。"
        "今日要丰富：下一节主卡片、本周数字、今日时间线、之后几周预告、空教室/考试/同步快捷入口。"
        "课表是 7 天×8 大节网格，当前周实色，未开课周次降低透明度。"
        "成绩有学分环和在读课程列表；我的分未登录（表单就在页内）和已登录两种状态。"
        "示例学期 2026-2027-1，第3周周二主要是军事理论。"
        "实现用 Miuix：FloatingNavigationBar(iosLike)、Card、Text、SmallTitle、TextField、Button、"
        "SwitchPreference、ArrowPreference、TabRow、ProgressIndicator。"
    ),
    "platform": "cmp",
    "theme": {"mode": "light", "seed": "#3482FF", "monet": False},
    "screens": [
        screen(
            "today",
            "今日",
            0,
            [
                item("text", 16, 16, 380, 24, "下午好 · 本地课表", id="td-hi", textStyle="footnote1"),
                item("text", 16, 40, 380, 36, "9月15日 周二", id="td-title", textStyle="title2"),
                item(
                    "text",
                    16,
                    76,
                    380,
                    24,
                    "2026-2027 第1学期 · 第3周 · 江门校区",
                    id="td-sub",
                    textStyle="body2",
                ),
                item(
                    "card",
                    16,
                    112,
                    380,
                    120,
                    "军事理论",
                    id="td-hero",
                    supporting="下一节 · 大约 4 小时后\n11-12节 · 学生发展中心601(江) · 常家瑶 · 考查",
                    to="detail-junli",
                    transition="slide",
                    color="#3482FF",
                ),
                item("progress", 32, 240, 348, 6, "", id="td-bar", variant="linear", value=0.62),
                item(
                    "card",
                    16,
                    260,
                    88,
                    72,
                    "1",
                    id="td-s1",
                    supporting="今日课程",
                ),
                item("card", 112, 260, 88, 72, "5", id="td-s2", supporting="本周军理"),
                item("card", 208, 260, 88, 72, "13", id="td-s3", supporting="学期课程"),
                item("card", 304, 260, 88, 72, "18", id="td-s4", supporting="教学周"),
                item("smallTitle", 0, 344, 412, 40, "今日安排", id="td-sec1"),
                item(
                    "card",
                    16,
                    384,
                    380,
                    88,
                    "11-12节  军事理论",
                    id="td-row",
                    supporting="学生发展中心601(江) · 常家瑶 · 第3周",
                    to="detail-junli",
                    transition="slide",
                ),
                item("smallTitle", 0, 480, 412, 40, "之后几周会变满", id="td-sec2"),
                item(
                    "card",
                    16,
                    520,
                    380,
                    88,
                    "第7周 · 高数 / 电子 / C语言",
                    id="td-later",
                    supporting="第10周再加导论、办公软件、体育和 Web",
                    to="week",
                    transition="fade",
                ),
                item("smallTitle", 0, 616, 412, 40, "快捷", id="td-sec3"),
                item("card", 16, 656, 120, 72, "空教室", id="td-q1", supporting="查询空闲教室"),
                item("card", 146, 656, 120, 72, "考试", id="td-q2", supporting="暂无安排"),
                item("card", 276, 656, 120, 72, "同步", id="td-q3", supporting="去我的登录", to="me", transition="fade"),
                glass_nav(0, "td"),
            ],
            note="启动页。不要登录墙。下一节主卡 + 四个数字 + 时间线 + 预告 + 快捷。底栏 iosLike 玻璃悬浮。",
            swipe={"left": "week"},
        ),
        screen(
            "week",
            "课表",
            1,
            [
                item("text", 16, 16, 380, 36, "课表", id="wk-title", textStyle="title2"),
                item("text", 16, 52, 380, 24, "未开课已淡化 · 点课程看详情", id="wk-sub", textStyle="body2"),
                item(
                    "tabRow",
                    16,
                    84,
                    380,
                    42,
                    "周次",
                    id="wk-weeks",
                    variant="contour",
                    selected=2,
                    tabs=[
                        {"icon": "", "label": "1周"},
                        {"icon": "", "label": "2周"},
                        {"icon": "", "label": "3周"},
                        {"icon": "", "label": "7周"},
                        {"icon": "", "label": "10周"},
                        {"icon": "", "label": "全部"},
                    ],
                ),
                item(
                    "tabRow",
                    16,
                    136,
                    380,
                    42,
                    "星期",
                    id="wk-days",
                    variant="default",
                    selected=1,
                    tabs=[
                        {"icon": "", "label": "一"},
                        {"icon": "", "label": "二"},
                        {"icon": "", "label": "三"},
                        {"icon": "", "label": "四"},
                        {"icon": "", "label": "五"},
                        {"icon": "", "label": "六"},
                        {"icon": "", "label": "日"},
                    ],
                ),
                item(
                    "card",
                    16,
                    188,
                    380,
                    88,
                    "1-2节  高等数学I(理)",
                    id="wk-c1",
                    supporting="第7-18周 · H2-510（江） · 张乐怡 · 本周未开始",
                    to="detail-math",
                    transition="slide",
                ),
                item(
                    "card",
                    16,
                    284,
                    380,
                    100,
                    "11-12节  军事理论",
                    id="wk-c2",
                    supporting="第3周 · 学生发展中心601(江) · 常家瑶 · 今天\n同格第10周起是计算机与人工智能导论",
                    to="detail-junli",
                    transition="slide",
                    color="#FFD9D6",
                ),
                item(
                    "card",
                    16,
                    392,
                    380,
                    88,
                    "11-12节  计算机与人工智能导论",
                    id="wk-c3",
                    supporting="第10-18周 · H3-205(江) · 白晴 · 本周未开始",
                    to="detail-ai",
                    transition="slide",
                ),
                item(
                    "card",
                    16,
                    488,
                    380,
                    88,
                    "15-16节  电子技术基础",
                    id="wk-c4",
                    supporting="第7-15周 · H3-106(江) · 刘晓明 · 本周未开始",
                    to="detail-ee",
                    transition="slide",
                ),
                item(
                    "text",
                    16,
                    588,
                    380,
                    44,
                    "7×8 色块网格在 preview.html。Canvas 用按天卡片方便点选。",
                    id="wk-note",
                    textStyle="footnote1",
                    multiline=True,
                ),
                glass_nav(1, "wk"),
            ],
            note="周课表。周次 Tab + 星期 Tab + 当天课程卡片。玻璃悬浮底栏。",
            swipe={"right": "today", "left": "grades"},
        ),
        screen(
            "detail-math",
            "高数详情",
            2,
            [
                item(
                    "topAppBar",
                    0,
                    0,
                    412,
                    72,
                    "课程详情",
                    id="dm-bar",
                    variant="small",
                    to="week",
                    transition="slideLeft",
                ),
                item("text", 16, 88, 380, 36, "高等数学I(理)", id="dm-title", textStyle="title2"),
                item("text", 16, 128, 380, 28, "专业必修 · 4 学分 · 考试", id="dm-chips", textStyle="body2"),
                item(
                    "card",
                    16,
                    168,
                    380,
                    72,
                    "一周三次",
                    id="dm-sum",
                    supporting="教室在 H1-208 和 H2-510，第7周开始。",
                ),
                item("smallTitle", 0, 252, 412, 40, "上课安排", id="dm-sec1"),
                item("arrowPref", 16, 292, 380, 80, "教师", id="dm-t", supporting="张乐怡"),
                item("arrowPref", 16, 372, 380, 80, "教室", id="dm-r", supporting="周一 H1-208 · 周二/周三 H2-510"),
                item("arrowPref", 16, 452, 380, 80, "时间", id="dm-time", supporting="一 3-4 · 二 1-2 · 三 9-10"),
                item("arrowPref", 16, 532, 380, 80, "周次", id="dm-w", supporting="7-18周"),
                item("smallTitle", 0, 620, 412, 40, "课程信息", id="dm-sec2"),
                item("arrowPref", 16, 660, 380, 56, "课程号", id="dm-kch", supporting="GE1031"),
                item("arrowPref", 16, 716, 380, 80, "教学班", id="dm-jxb", supporting="(2026-2027-1)-GE1031-20"),
                item("button", 16, 812, 182, 50, "返回今日", id="dm-back", variant="secondary", to="today"),
                item("button", 214, 812, 182, 50, "看周课表", id="dm-week", variant="primary", to="week"),
            ],
            note="详情页无底栏，保留返回。",
        ),
        screen(
            "detail-junli",
            "军理详情",
            3,
            [
                item("topAppBar", 0, 0, 412, 72, "课程详情", id="dj-bar", variant="small", to="today"),
                item("text", 16, 88, 380, 36, "军事理论", id="dj-title", textStyle="title2"),
                item("text", 16, 128, 380, 28, "专业必修 · 2 学分 · 考查", id="dj-chips", textStyle="body2"),
                item(
                    "card",
                    16,
                    168,
                    380,
                    88,
                    "第3周几乎每天 11-12节",
                    id="dj-sum",
                    supporting="教室在学生发展中心601。第5周起主要周五，教室改 S-101。",
                ),
                item("smallTitle", 0, 268, 412, 40, "上课安排", id="dj-sec1"),
                item("arrowPref", 16, 308, 380, 80, "教师", id="dj-t", supporting="常家瑶"),
                item("arrowPref", 16, 388, 380, 80, "教室", id="dj-r", supporting="2-3周 发展中心601 · 5-14周周五 S-101"),
                item("arrowPref", 16, 468, 380, 80, "时间", id="dj-time", supporting="第3周每天 11-12节"),
                item("arrowPref", 16, 548, 380, 80, "周次", id="dj-w", supporting="2-3周，5-14周"),
                item("button", 16, 648, 182, 50, "返回今日", id="dj-today", variant="secondary", to="today"),
                item("button", 214, 648, 182, 50, "看周课表", id="dj-week", variant="primary", to="week"),
            ],
        ),
        screen(
            "detail-ai",
            "导论详情",
            4,
            [
                item("topAppBar", 0, 0, 412, 72, "课程详情", id="da-bar", variant="small", to="week"),
                item("text", 16, 88, 380, 36, "计算机与人工智能导论", id="da-title", textStyle="title2"),
                item("text", 16, 128, 380, 28, "专业必修 · 2 学分 · 考试", id="da-chips", textStyle="body2"),
                item("arrowPref", 16, 176, 380, 80, "教师", id="da-t", supporting="白晴"),
                item("arrowPref", 16, 256, 380, 80, "教室", id="da-r", supporting="周一 H3-104 · 周二 H3-205"),
                item("arrowPref", 16, 336, 380, 80, "时间", id="da-time", supporting="周一 1-2节 · 周二 11-12节"),
                item("arrowPref", 16, 416, 380, 80, "周次", id="da-w", supporting="10-18周"),
                item("button", 16, 516, 380, 50, "返回课表", id="da-back", variant="secondary", to="week"),
            ],
        ),
        screen(
            "detail-ee",
            "电子详情",
            5,
            [
                item("topAppBar", 0, 0, 412, 72, "课程详情", id="de-bar", variant="small", to="week"),
                item("text", 16, 88, 380, 36, "电子技术基础", id="de-title", textStyle="title2"),
                item("text", 16, 128, 380, 28, "专业必修 · 3 学分 · 考试", id="de-chips", textStyle="body2"),
                item("arrowPref", 16, 176, 380, 80, "教师", id="de-t", supporting="刘晓明"),
                item("arrowPref", 16, 256, 380, 80, "教室", id="de-r", supporting="H3-106 · 周三晚 S-204"),
                item("arrowPref", 16, 336, 380, 80, "时间", id="de-time", supporting="周二 15-16 · 周三 1-2 / 15-16"),
                item("arrowPref", 16, 416, 380, 80, "周次", id="de-w", supporting="7-15周"),
                item("button", 16, 516, 380, 50, "返回课表", id="de-back", variant="secondary", to="week"),
            ],
        ),
        screen(
            "grades",
            "成绩",
            6,
            [
                item("text", 16, 16, 380, 36, "成绩", id="g-title", textStyle="title2"),
                item("text", 16, 52, 380, 24, "出分后按学期收进这里", id="g-sub", textStyle="body2"),
                item(
                    "card",
                    16,
                    88,
                    380,
                    100,
                    "已修学分  0",
                    id="g-ring",
                    supporting="本学期计划约 28 学分。军理、高数、英语会先出分。",
                ),
                item("progress", 32, 196, 348, 6, "", id="g-bar", variant="linear", value=0.04),
                item("card", 16, 216, 88, 72, "—", id="g-s1", supporting="平均学分绩"),
                item("card", 112, 216, 88, 72, "0", id="g-s2", supporting="已出成绩"),
                item("card", 208, 216, 88, 72, "0", id="g-s3", supporting="考试安排"),
                item("card", 304, 216, 88, 72, "13", id="g-s4", supporting="在读课程"),
                item("smallTitle", 0, 300, 412, 40, "本学期", id="g-sec"),
                item("arrowPref", 16, 340, 380, 80, "高等数学I(理)", id="g-r1", supporting="未出分 · 4 学分"),
                item("arrowPref", 16, 420, 380, 80, "大学英语I（综合基础）", id="g-r2", supporting="未出分 · 4 学分"),
                item("arrowPref", 16, 500, 380, 80, "军事理论", id="g-r3", supporting="考查 · 2 学分"),
                item("arrowPref", 16, 580, 380, 80, "电子技术基础", id="g-r4", supporting="未出分 · 3 学分"),
                glass_nav(2, "g"),
            ],
            note="空成绩也要有结构：学分环、四个数字、在读课程列表。",
            swipe={"right": "week", "left": "me"},
        ),
        screen(
            "me",
            "我的",
            7,
            [
                item("text", 16, 16, 380, 36, "我的", id="me-title", textStyle="title2"),
                item("text", 16, 52, 380, 24, "课表可以先看本地，登录后同步教务", id="me-sub", textStyle="body2"),
                item(
                    "card",
                    16,
                    88,
                    380,
                    88,
                    "未登录",
                    id="me-card",
                    supporting="正方教务 · jwxt.gzus.edu.cn",
                ),
                item("textField", 16, 188, 380, 64, "学号", id="me-id"),
                item("textField", 16, 260, 380, 64, "密码", id="me-pw"),
                item(
                    "button",
                    16,
                    336,
                    380,
                    50,
                    "登录并同步",
                    id="me-go",
                    variant="primary",
                    to="me-ok",
                    transition="fade",
                ),
                item("smallTitle", 0, 400, 412, 40, "登录后可用", id="me-sec1"),
                item("card", 16, 440, 120, 72, "空教室", id="me-q1", supporting="N2155"),
                item("card", 146, 440, 120, 72, "选课", id="me-q2", supporting="自主选课"),
                item("card", 276, 440, 120, 72, "考试", id="me-q3", supporting="暂无"),
                item("smallTitle", 0, 524, 412, 40, "课表", id="me-sec2"),
                item("switchPref", 16, 564, 380, 80, "上课提醒", id="me-alert", supporting="课前 15 分钟", checked=True),
                item("switchPref", 16, 644, 380, 80, "显示周末", id="me-weekend", supporting="第2周军理会排到周六日", checked=True),
                glass_nav(3, "me"),
            ],
            note="登录只放在我的。页内学号密码 + 登录并同步。未登录也能先看本地课表。",
            swipe={"right": "grades"},
        ),
        screen(
            "me-ok",
            "我的已登录",
            8,
            [
                item("text", 16, 16, 380, 36, "我的", id="ok-title", textStyle="title2"),
                item(
                    "card",
                    16,
                    60,
                    380,
                    100,
                    "李致远",
                    id="ok-card",
                    supporting="端侧AI产业班 1班 · 2640153101\n同步于刚刚",
                ),
                item("card", 16, 172, 88, 72, "13", id="ok-s1", supporting="课程"),
                item("card", 112, 172, 88, 72, "3", id="ok-s2", supporting="当前周"),
                item("card", 208, 172, 88, 72, "江", id="ok-s3", supporting="校区"),
                item("card", 304, 172, 88, 72, "OK", id="ok-s4", supporting="会话"),
                item("smallTitle", 0, 256, 412, 40, "教务", id="ok-sec1"),
                item("card", 16, 296, 120, 72, "空教室", id="ok-q1", supporting="查询空闲"),
                item("card", 146, 296, 120, 72, "选课", id="ok-q2", supporting="自主选课"),
                item("card", 276, 296, 120, 72, "培养方案", id="ok-q3", supporting="个人方案"),
                item("smallTitle", 0, 380, 412, 40, "设置", id="ok-sec2"),
                item("switchPref", 16, 420, 380, 80, "上课提醒", id="ok-alert", supporting="课前 15 分钟", checked=True),
                item("switchPref", 16, 500, 380, 80, "深色模式", id="ok-dark", supporting="跟随系统", checked=False),
                item(
                    "dropdownPref",
                    16,
                    580,
                    380,
                    80,
                    "当前学期",
                    id="ok-term",
                    supporting="2026-2027 第1学期",
                    variant="overlay",
                    selected=0,
                    tabs=[{"icon": "", "label": "2026-2027 第1学期"}],
                ),
                item("arrowPref", 16, 660, 380, 80, "退出登录", id="ok-out", supporting="清除会话", to="me", transition="fade"),
                glass_nav(3, "ok", me="me-ok"),
            ],
            note="登录成功后的我的：资料、四个数字、教务入口、设置、退出回到未登录。",
        ),
    ],
}

OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
raw = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
packed = zlib.compress(raw, level=9, wbits=-15)
b64 = base64.b64encode(packed).decode("ascii").replace("+", "-").replace("/", "_").rstrip("=")
url = f"https://lazyboneslzy.github.io/miuix-canvas/#dz={b64}"
SHARE.write_text(url + "\n", encoding="utf-8")
print("json", OUT.stat().st_size, "share", len(url))
