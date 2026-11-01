package com.example.gemini.data.agent.claude

/**
 * Built-in presets for Anthropic-compatible providers, so a user does not have to type model ids by hand.
 *
 * OpenRouter is served by the bridge with its Anthropic-compatible endpoint and already knows how to list its
 * free models. The ids below are the ones that call tools (Claude Code needs tool calling); OpenRouter's free
 * catalogue changes often, so "Fetch free models" on the provider stays the source of truth for new free ids.
 */
object ClaudeProviderPresets {

    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api"
    const val OPENROUTER_NAME = "OpenRouter"

    /** One quick-add group shown as a preset button in the providers page. */
    data class Preset(
        val label: String,
        val description: String,
        val models: List<ClaudeProviderModel>
    )

    val deepSeek = Preset(
        label = "DeepSeek Free",
        description = "DeepSeek V4.1 Flash (free tier when available) and DeepSeek Chat v3",
        models = listOf(
            ClaudeProviderModel("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash"),
            ClaudeProviderModel("deepseek/deepseek-chat-v3-0324", "DeepSeek Chat v3")
        )
    )

    val qwen = Preset(
        label = "Qwen",
        description = "Qwen3 Coder and Qwen3 235B A22B",
        models = listOf(
            ClaudeProviderModel("qwen/qwen3-coder", "Qwen3 Coder"),
            ClaudeProviderModel("qwen/qwen3-235b-a22b-2507", "Qwen3 235B A22B")
        )
    )

    val llama = Preset(
        label = "Llama",
        description = "Meta Llama 3.3 70B and Llama 4 Scout",
        models = listOf(
            ClaudeProviderModel("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B"),
            ClaudeProviderModel("meta-llama/llama-4-scout", "Llama 4 Scout")
        )
    )

    val gemini = Preset(
        label = "Google Gemini",
        description = "Google Gemini 2.5 Flash and Flash Lite",
        models = listOf(
            ClaudeProviderModel("google/gemini-2.5-flash", "Google Gemini 2.5 Flash"),
            ClaudeProviderModel("google/gemini-2.5-flash-lite", "Google Gemini 2.5 Flash Lite")
        )
    )

    val freeAndOpen = Preset(
        label = "Free & open",
        description = "Google Gemma 4 models that are free on OpenRouter",
        models = listOf(
            ClaudeProviderModel("google/gemma-4-31b-it:free", "Google Gemma 4 31B (free)"),
            ClaudeProviderModel("google/gemma-4-26b-a4b-it:free", "Google Gemma 4 26B A4B (free)")
        )
    )

    val all: List<Preset> = listOf(deepSeek, qwen, llama, gemini, freeAndOpen)

    /** Every preset model, de-duplicated by id; what a fresh OpenRouter provider is seeded with. */
    val defaultModels: List<ClaudeProviderModel> = all
        .flatMap { it.models }
        .distinctBy { it.id }

    fun isOpenRouter(baseUrl: String): Boolean =
        baseUrl.contains("openrouter.ai", ignoreCase = true)

    /** Adds [models] to [existing], skipping ids that are already there. */
    fun merge(
        existing: List<ClaudeProviderModel>,
        models: List<ClaudeProviderModel>
    ): List<ClaudeProviderModel> {
        val known = existing.map { it.id }.toSet()
        return existing + models.filter { it.id !in known }
    }
}
