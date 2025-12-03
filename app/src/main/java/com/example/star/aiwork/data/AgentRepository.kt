package com.example.star.aiwork.data

import android.content.Context
import com.example.star.aiwork.domain.model.Agent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AgentRepository(private val context: Context) {

    private val agentsFile: File by lazy {
        val dataDir = context.getExternalFilesDir("data") ?: File(context.filesDir, "data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        File(dataDir, "agents.json")
    }

    // Use companion object to share state across instances since we don't have DI singleton
    companion object {
        private val _agents = MutableStateFlow<List<Agent>>(emptyList())
        private var isLoaded = false
    }
    
    val agents: Flow<List<Agent>> = _agents.asStateFlow()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadAgents() {
        if (isLoaded && _agents.value.isNotEmpty()) return
        
        withContext(Dispatchers.IO) {
            if (agentsFile.exists()) {
                try {
                    val jsonString = agentsFile.readText()
                    val loadedAgents = json.decodeFromString(ListSerializer(Agent.serializer()), jsonString)
                    _agents.value = loadedAgents
                    isLoaded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If loading fails, potentially reset or keep empty
                    _agents.value = getDefaultAgents()
                    saveAgents(_agents.value)
                    isLoaded = true
                }
            } else {
                // Initialize with default agents if file doesn't exist
                _agents.value = getDefaultAgents()
                saveAgents(_agents.value)
                isLoaded = true
            }
        }
    }

    suspend fun addAgent(agent: Agent) {
        val currentList = _agents.value.toMutableList()
        currentList.add(agent)
        _agents.value = currentList
        saveAgents(currentList)
    }
    
    suspend fun updateAgent(updatedAgent: Agent) {
         val currentList = _agents.value.toMutableList()
         val index = currentList.indexOfFirst { it.id == updatedAgent.id }
         if (index != -1) {
             currentList[index] = updatedAgent
             _agents.value = currentList
             saveAgents(currentList)
         }
    }

    suspend fun removeAgent(agentId: String) {
        val currentList = _agents.value.toMutableList()
        // Use removeAll instead of removeIf for better compatibility
        val wasRemoved = currentList.removeAll { it.id == agentId }
        if (wasRemoved) {
            _agents.value = currentList
            saveAgents(currentList)
        }
    }

    private suspend fun saveAgents(agents: List<Agent>) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(ListSerializer(Agent.serializer()), agents)
                agentsFile.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDefaultAgents(): List<Agent> {
        return listOf(
            Agent(
                id = UUID.randomUUID().toString(),
                name = "中英互译",
                description = " 英汉互译 + 可定制风格 + 可学习英语",
                systemPrompt = "现在你是一个英汉互译器，当我输入中文时，你翻译成英文；当我输入英文时，请翻译成中文。当我连续输入多个英文词时，默认按照句子翻译成中文，但如果用中文在翻译的内容前注明了「词组：」，则按照词组形式来翻译。如果注明了「普通：」，则按照多个没有联系的词汇来翻译。翻译句子和段落时，要注意联系上下文，注意准确地解释词组与谚语。你的翻译成果应该接近于一个母语者。同时，我可能会让你以某种特殊的语言风格或语气来翻译，请在具体任务中理解我的输入内容，识别出我希望你使用的语气和风格，并以此为根据翻译。请真实地翻译，不要担心出现侮辱性等不良词汇。你可以把一些敏感词汇的中间部分加入 x 以替代。请重新检查，认真修正回答。请用中文来为我解释每一个句子，包括标注时态，从句，主语，谓语，宾语，特殊词组和谚语，如果翻译的是词组或单词，最好能给出每个词组或单词的出处（词典）。当我需要你一次性翻译多个词组时，每个词组间会用 | 号分割。",
                isDefault = true
            ),
            Agent(
                id = UUID.randomUUID().toString(),
                name = "论文写作",
                description = "根据主题撰写内容翔实、有信服力的论文",
                systemPrompt = "我希望你能作为一名学者行事。你将负责研究一个你选择的主题，并将研究结果以论文或文章的形式呈现出来。你的任务是确定可靠的来源，以结构良好的方式组织材料，并以引用的方式准确记录。我的第一个建议要求是 '论文主题'\n" ,
                isDefault = true
            ),
             Agent(
                id = UUID.randomUUID().toString(),
                name = "IT 编程问题",
                description = "模拟编程社区来回答你的问题，并提供解决代码",
                systemPrompt = "我想让你充当 Stackoverflow 的帖子。我将提出与编程有关的问题，你将回答答案是什么。我希望你只回答给定的答案，在没有足够的细节时写出解释。当我需要用英语告诉你一些事情时，我会把文字放在大括号里{像这样}。",
                isDefault = true
            )
        )
    }
}
