from __future__ import annotations

import json
import logging
from urllib import error, request
from typing import Optional, Tuple

from config import get_settings


logger = logging.getLogger(__name__)
settings = get_settings()


def build_local_reply(content: str) -> str:
    text = content.lower()
    if any(keyword in text for keyword in ["怕", "害怕", "unsafe", "scared"]):
        return "我在这里陪着你。如果你感到不安全，先往明亮有人处走，同时准备联系守护者或触发 SOS。"
    if any(keyword in text for keyword in ["到家", "arrived", "到了"]):
        return "快到家啦，太好了。记得进门后也报个平安，我会继续陪着你。"
    if any(keyword in text for keyword in ["累", "tired", "紧张", "anxious"]):
        return "辛苦了，先慢一点也没关系。你现在最重要的是保持冷静，我会陪你一起走完这段路。"
    return "收到，我会一直陪着你。如果有任何不舒服或异常情况，马上告诉我。"


PROACTIVE_MESSAGES = {
    "start": "夜深了，我会一直陪着你走到家的。一路注意安全哦！",
    "periodic": "你已经走了一段路啦，加油！还顺利吗？",
    "timeout": "你好像比预计晚了一些，还顺利吗？需要帮助吗？",
    "deviation": "你好像走了不同的路，一切还好吗？",
    "idle": "你停下来有一会儿了，是在休息吗？还是需要帮助？",
}


def build_system_prompt(trip_context: Optional[str] = None) -> str:
    context_line = f"当前行程上下文：{trip_context}。" if trip_context else "当前没有行程上下文。"
    return (
        "你是 NyxGuard 的夜间安全陪伴助理。你的语气需要温和、镇定、简短、有安抚感。"
        "你不是警察，也不要夸大风险。优先帮助用户保持冷静、去往明亮有人处、联系守护者，"
        "并在明显危险时建议触发 SOS。"
        f"{context_line}"
    )


def generate_guardian_reply(content: str, trip_context: Optional[str] = None) -> Tuple[str, bool]:
    api_key = settings.openai_api_key
    if not api_key:
        return build_local_reply(content), True

    body = {
        "model": settings.openai_model,
        "input": [
            {"role": "system", "content": [{"type": "input_text", "text": build_system_prompt(trip_context)}]},
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

    return build_local_reply(content), True
