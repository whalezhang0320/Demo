package com.example.star.aiwork.data.database

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class RetrievalResult(
    val context: String,
    val debugLog: String
)

class LocalRAGService(private val context: Context, private val dao: KnowledgeDao) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.e("LocalRAGService", "Failed to init PDFBox", e)
        }
    }
    
    val knownFiles: Flow<List<String>> = dao.getDistinctSourceFilenames()

    // 1. 解析 PDF 并切片
    suspend fun indexPdf(uri: Uri) = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("LocalRAGService", "Cannot open input stream for URI: $uri")
                return@withContext
            }

            // Create a temporary file
            tempFile = File.createTempFile("pdf_import_", ".pdf", context.cacheDir)
            
            // Copy inputStream to tempFile
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 使用 PDFBox 加载
            val document = PDDocument.load(tempFile)
            
            try {
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true 
                
                // 提取全文
                val fullText = stripper.getText(document)

                if (fullText.isBlank()) {
                    Log.w("LocalRAGService", "PDF content is empty")
                    return@withContext
                }

                // 切片
                val chunks = splitTextIntoChunks(fullText, chunkSize = 500)
                
                // 存入数据库
                val fileName = getFileName(uri)
                val entities = chunks.map { 
                    KnowledgeChunk(sourceFilename = fileName, content = it) 
                }
                dao.insertChunks(entities)
                Log.d("LocalRAGService", "Indexed ${entities.size} chunks from $fileName")
            } finally {
                document.close()
            }
            
        } catch (e: Exception) {
            Log.e("LocalRAGService", "Error indexing PDF", e)
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                Log.w("LocalRAGService", "Failed to delete temp file", e)
            }
        }
    }
    
    suspend fun deleteKnowledgeBase(filename: String) {
        dao.deleteBySourceFilename(filename)
    }

    private fun splitTextIntoChunks(text: String, chunkSize: Int): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            val cleanedPara = paragraph.trim()
            if (cleanedPara.isEmpty()) continue

            if (currentChunk.length + cleanedPara.length > chunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                
                if (cleanedPara.length > chunkSize) {
                     cleanedPara.chunked(chunkSize).forEach { 
                         chunks.add(it)
                     }
                } else {
                    currentChunk.append(cleanedPara).append("\n\n")
                }
            } else {
                currentChunk.append(cleanedPara).append("\n\n")
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        if (chunks.isEmpty() && text.isNotEmpty()) {
            return text.chunked(chunkSize)
        }
        
        return chunks
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown_file.pdf"
    }

    // 2. 检索 (Recall + Re-rank)
    suspend fun retrieve(query: String): RetrievalResult {
        if (query.isBlank()) return RetrievalResult("", "")
        try {
            // A. 预处理查询
            val ftsQuery = formatFtsQuery(query) 
            
            // B. 召回 (Recall): 获取 Top 20 候选
            // 注意：candidates 的顺序就是 FTS 认为的顺序 (基于 BM25 等)
            val candidates = dao.search(ftsQuery)
            
            if (candidates.isEmpty()) {
                 return RetrievalResult("", "No results found for query: $query")
            }

            // C. 重排序 (Re-ranking): 内存中精细打分
            val queryTerms = extractQueryTerms(query)
            
            // 我们创建一个包含 (Chunk, Score, OriginalRank) 的列表
            val scoredCandidates = candidates.mapIndexed { index, chunk ->
                val score = calculateRelevanceScore(queryTerms, chunk.content)
                Triple(chunk, score, index + 1) // index+1 是原始 FTS 排名
            }

            // 按照分数降序排序
            val topResults = scoredCandidates
                .sortedByDescending { it.second } 
                .take(5)
            
            // D. 构建上下文 (Context Construction)
            val context = topResults.map { it.first }
                .distinctBy { it.content }
                .joinToString("\n\n---\n\n") { chunk ->
                    "【来源: ${chunk.sourceFilename}】\n${chunk.content}"
                }

            // E. 构建直观的分析日志 (Visual Debug Log)
            val logBuilder = StringBuilder()
            logBuilder.append("\n\n💡 [RAG 算法分析面板]\n")
            logBuilder.append("--------------------------------------------------\n")
            logBuilder.append("🔍 提取关键词: ${queryTerms.joinToString(", ")}\n")
            logBuilder.append("📊 召回数量: ${candidates.size} (FTS), 精选: ${topResults.size} (Re-rank)\n\n")
            
            topResults.forEachIndexed { i, (chunk, score, originalRank) ->
                val rankChange = if (originalRank > (i + 1)) "⬆️(原#$originalRank)" else "-(原#$originalRank)"
                // 截取内容预览
                val preview = chunk.content.replace("\n", " ").take(30) + "..."
                
                logBuilder.append("${i + 1}. [Score: ${"%.2f".format(score)}] $rankChange\n")
                logBuilder.append("   📄 ${chunk.sourceFilename}\n")
                logBuilder.append("   📝 \"$preview\"\n")
            }
            logBuilder.append("--------------------------------------------------")

            // 打印日志到 Logcat
            Log.d("LocalRAGService", logBuilder.toString())

            return RetrievalResult(context, logBuilder.toString())

        } catch (e: Exception) {
            Log.e("LocalRAGService", "Error retrieving context", e)
            return RetrievalResult("", "Error: ${e.message}")
        }
    }
    
    private fun formatFtsQuery(query: String): String {
        val sanitized = query.replace(Regex("[^\\w\\s\\u4e00-\\u9fa5]"), " ")
        val words = sanitized.trim().split("\\s+".toRegex())
        return words.filter { it.isNotBlank() }.joinToString(" OR ") { "$it*" }
    }

    private fun extractQueryTerms(query: String): Set<String> {
        return query.lowercase()
            .split(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun calculateRelevanceScore(queryTerms: Set<String>, content: String): Double {
        if (queryTerms.isEmpty()) return 0.0
        val contentLower = content.lowercase()
        
        val matchedTermsCount = queryTerms.count { term ->
            contentLower.contains(term)
        }
        
        val coverage = matchedTermsCount.toDouble() / queryTerms.size
        return coverage
    }
    
    suspend fun clearAll() {
        dao.clearAll()
    }
}
