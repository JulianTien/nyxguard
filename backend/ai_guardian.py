from __future__ import annotations

import json
import logging
import re
from urllib import error, request
from typing import Optional, Tuple

from config import get_settings


logger = logging.getLogger(__name__)
settings = get_settings()

_CJK_PATTERN = re.compile(r"[\u4e00-\u9fff]")


def is_probably_english(content: str, accept_language: Optional[str] = None) -> bool:
    language = (accept_language or "").lower()
    if language.startswith("en"):
        return True
    if language.startswith("zh"):
        return False

    has_cjk = bool(_CJK_PATTERN.search(content))
    has_ascii_letters = any("a" <= ch.lower() <= "z" for ch in content)
    return has_ascii_letters and not has_cjk


def build_local_reply(content: str, prefer_english: Optional[bool] = None) -> str:
    text = content.lower()
    prefer_english = is_probably_english(content) if prefer_english is None else prefer_english
    if any(keyword in text for keyword in ["怕", "害怕", "unsafe", "scared", "afraid", "follow", "following"]):
        if prefer_english:
            return (
                "I'm here with you. If you feel unsafe, head toward a brighter and busier place first, "
                "and be ready to contact a guardian or trigger SOS."
            )
        return "我在这里陪着你。如果你感到不安全，先往明亮有人处走，同时准备联系守护者或触发 SOS。"
    if any(keyword in text for keyword in ["到家", "arrived", "到了"]):
        if prefer_english:
            return "You're almost home. Once you're inside, send a quick update and I'll stay with you until then."
        return "快到家啦，太好了。记得进门后也报个平安，我会继续陪着你。"
    if any(keyword in text for keyword in ["累", "tired", "紧张", "anxious"]):
        if prefer_english:
            return "You've done a lot already. Slow your pace a little if you need to, and let's keep getting you home calmly."
        return "辛苦了，先慢一点也没关系。你现在最重要的是保持冷静，我会陪你一起走完这段路。"
    if prefer_english:
        return "Got it. I'm staying with you, so tell me right away if anything feels off or uncomfortable."
    return "收到，我会一直陪着你。如果有任何不舒服或异常情况，马上告诉我。"


PROACTIVE_MESSAGES = {
    "start": "夜深了，我会一直陪着你走到家的。一路注意安全哦！",
    "periodic": "你已经走了一段路啦，加油！还顺利吗？",
    "timeout": "你好像比预计晚了一些，还顺利吗？需要帮助吗？",
    "deviation": "你好像走了不同的路，一切还好吗？",
    "idle": "你停下来有一会儿了，是在休息吗？还是需要帮助？",
}

PROACTIVE_MESSAGES_EN = {
    "start": "You're out late, and I'm staying with you until you get home. Keep to well-lit areas and check in anytime.",
    "periodic": "Quick check-in: how is the route feeling right now? You're doing well, just keep a steady pace.",
    "timeout": "It looks like you're taking longer than expected. Are you okay, and do you want extra support right now?",
    "deviation": "It looks like your route changed a bit. Is everything okay on your side?",
    "idle": "You've been stopped for a little while. Are you taking a break, or do you need help?",
}


def get_proactive_message(trigger: str, prefer_english: bool = False) -> str:
    table = PROACTIVE_MESSAGES_EN if prefer_english else PROACTIVE_MESSAGES
    return table.get(trigger, table["periodic"])


def build_system_prompt(trip_context: Optional[str] = None, prefer_english: bool = False) -> str:
    if prefer_english:
        context_line = f"Current trip context: {trip_context}." if trip_context else "There is no active trip context."
        return (
            "You are NyxGuard's nighttime safety companion. Your tone should be warm, calm, brief, and grounding. "
            "You are not the police, and you should not exaggerate risk. Help the user stay calm, move toward brighter "
            "or busier places, contact guardians when helpful, and recommend SOS only when there is clear danger. "
            "Reply in natural English."
            f" {context_line}"
        )

    context_line = f"当前行程上下文：{trip_context}。" if trip_context else "当前没有行程上下文。"
    return (
        "你是 NyxGuard 的夜间安全陪伴助理。你的语气需要温和、镇定、简短、有安抚感。"
        "你不是警察，也不要夸大风险。优先帮助用户保持冷静、去往明亮有人处、联系守护者，"
        "并在明显危险时建议触发 SOS。"
        f"{context_line}"
    )


def generate_guardian_reply(
    content: str,
    trip_context: Optional[str] = None,
    prefer_english: Optional[bool] = None,
) -> Tuple[str, bool]:
    prefer_english = is_probably_english(content) if prefer_english is None else prefer_english
    api_key = settings.openai_api_key
    if not api_key:
        return build_local_reply(content, prefer_english=prefer_english), True

    body = {
        "model": settings.openai_model,
        "input": [
            {
                "role": "system",
                "content": [{"type": "input_text", "text": build_system_prompt(trip_context, prefer_english=prefer_english)}],
            },
            {"role": "user", "content": [{"type": "input_text", "text": content}]},
        ],
    }
    payload = json.dumps(body).encode("utf-8")
    req = request.Request(
        url=f"{settings.openai_base_url.rstrip('/')}/responses",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    try:
        with request.urlopen(req, timeout=12) as response:
            parsed = json.loads(response.read().decode("utf-8"))
            text = parsed.get("output_text", "").strip()
            if text:
                return text, False
    except (error.URLError, TimeoutError, json.JSONDecodeError, ValueError) as exc:
        logger.warning("openai reply failed; falling back to local reply: %s", exc)

    return build_local_reply(content, prefer_english=prefer_english), True
