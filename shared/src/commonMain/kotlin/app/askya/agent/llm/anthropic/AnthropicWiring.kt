package app.askya.agent.llm.anthropic

import app.askya.agent.AgentPolicy
import app.askya.data.preferences.AgentPreferences

/*
 * Как настройки агента превращаются в клиента и политику — одно место для
 * контейнера и для тестов.
 */

/**
 * Политика на сейчас — из согласия этого устройства. Сессия зовёт её перед
 * каждой отправкой, поэтому выключенное облако действует со следующего же
 * запроса, даже посреди хода.
 */
fun agentPolicyOf(agent: AgentPreferences): () -> AgentPolicy = { AgentPolicy(allowCloud = agent.cloudAllowed) }

/**
 * Клиент Claude с ключом из настроек или `null`, если ключа нет. Создаётся
 * один раз на разговор: сессия держит его до конца, а новый ключ достаётся
 * новому разговору.
 */
suspend fun anthropicClientOf(agent: AgentPreferences): AnthropicLlmClient? =
    anthropicClientOf(agent, UrlConnectionTransport())

internal suspend fun anthropicClientOf(agent: AgentPreferences, transport: HttpTransport): AnthropicLlmClient? =
    agent.apiKey()?.let { key -> AnthropicLlmClient(AnthropicConfig(apiKey = key), transport) }
