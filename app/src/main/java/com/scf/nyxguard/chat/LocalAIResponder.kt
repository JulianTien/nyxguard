package com.scf.nyxguard.chat

import com.scf.nyxguard.LocaleManager

/**
 * 本地 AI 回复模板（无后端时的兜底方案）。
 * AI 人设：温暖守护者，语气亲切关怀。
 */
object LocalAIResponder {

    private val greetingsZh = listOf(
        "你好呀！我是 NyxGuard 的 AI 小助手，有什么可以帮你的吗？",
        "嗨，很高兴见到你！今晚有出行计划吗？",
        "你好！我会一直陪着你，有任何事情随时告诉我哦。"
    )

    private val greetingsEn = listOf(
        "Hi! I'm NyxGuard's AI helper. How can I support you right now?",
        "Hey, it's good to see you. Do you have travel plans tonight?",
        "Hello! I'll stay with you the whole way, so tell me anything you need."
    )

    private val fearResponsesZh = listOf(
        "别害怕，我一直在这里陪着你。如果感到不安全，可以随时使用 SOS 功能哦。",
        "我能理解你的感受。要不要试试模拟来电功能？假装有人找你可能会让你安心一些。",
        "你不是一个人，我陪着你呢。如果情况紧急，记得长按 SOS 按钮。"
    )

    private val fearResponsesEn = listOf(
        "You are not alone. I'm right here with you, and you can use SOS anytime if you feel unsafe.",
        "I understand how that feels. If you want, you can try the fake call feature to create some space.",
        "Take it one step at a time. If this starts to feel urgent, long-press the SOS button right away."
    )

    private val anxietyResponsesZh = listOf(
        "深呼吸，慢慢来。你已经很勇敢了，走在回家的路上呢。",
        "放松一下，一切都会好的。要不要聊聊天，转移一下注意力？",
        "你做得很好！保持平常心，很快就到目的地了。"
    )

    private val anxietyResponsesEn = listOf(
        "Take a slow breath. You're already doing something brave by getting yourself home safely.",
        "Try to relax your shoulders a little. Want to chat so we can take your mind off this?",
        "You're doing well. Stay steady and you'll be at your destination soon."
    )

    private val defaultResponsesZh = listOf(
        "我在呢，继续说。",
        "嗯嗯，我听着呢，还有什么想聊的吗？",
        "好的，记得注意安全哦！有什么需要随时告诉我。",
        "收到！如果路上有任何情况，随时叫我。",
        "了解啦，祝你一路平安！"
    )

    private val defaultResponsesEn = listOf(
        "I'm here. Go on.",
        "I'm listening. What else is on your mind?",
        "Okay. Keep staying aware, and tell me if anything changes.",
        "Got it. If anything happens on the way, call for me anytime.",
        "Understood. Wishing you a safe trip."
    )

    fun getResponse(userMessage: String): String {
        val msg = userMessage.lowercase()
        val isChinese = LocaleManager.isChinese()
        return when {
            msg.matches(Regex(".*(你好|嗨|在吗|hi|hello|hey).*")) ->
                if (isChinese) greetingsZh.random() else greetingsEn.random()

            msg.matches(Regex(".*(害怕|好黑|有人跟|恐怖|可怕|不敢|跟踪|scared|afraid|follow|unsafe).*")) ->
                if (isChinese) fearResponsesZh.random() else fearResponsesEn.random()

            msg.matches(Regex(".*(紧张|不安|心慌|焦虑|担心|慌|anxious|nervous|panic|worried).*")) ->
                if (isChinese) anxietyResponsesZh.random() else anxietyResponsesEn.random()

            msg.matches(Regex(".*(帮助|救命|危险|报警|sos|help|danger|police|emergency).*")) ->
                if (isChinese) {
                    "如果你感到危险，请立即使用 SOS 功能！长按首页的 SOS 按钮，我们会帮你联系守护者。"
                } else {
                    "If you feel in danger, use SOS right away. Long-press the SOS button on the home screen and we'll help contact your guardians."
                }

            msg.matches(Regex(".*(怎么用|功能|步行|乘车|来电|how|feature|walk|ride|call).*")) ->
                if (isChinese) {
                    "你可以在首页选择「步行守护」或「乘车守护」开始行程，我会实时守护你。如果需要脱身，试试「模拟来电」功能哦！"
                } else {
                    "You can start a trip from the home screen with Walk Guard or Ride Guard, and I'll stay with you in real time. If you need an exit strategy, try Fake Call."
                }

            else -> if (isChinese) defaultResponsesZh.random() else defaultResponsesEn.random()
        }
    }

    fun getWelcomeMessage(): String {
        return if (LocaleManager.isChinese()) {
            "你好！我是你的 AI 安全陪伴助手。夜间出行时，我会一直陪着你。有什么想聊的，尽管说吧！"
        } else {
            "Hi! I'm your AI safety companion. I'll stay with you during night travel, so feel free to talk to me anytime."
        }
    }
}
