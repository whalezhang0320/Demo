package com.example.star.aiwork.ui.conversation.util

import android.content.Context
import kotlin.collections.asList

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object PythonHelper {
    fun runPythonCode(context: Context, code: String): String {
        return try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()

            // 我们不直接操作 sys 模块，而是编写一段 Python 包装脚本
            // 这段脚本使用 contextlib 来安全地捕获 stdout 和 stderr
            // 避免了直接修改 sys.stdout 导致的 SystemError: frame does not exist
            val wrapperCode = """
import sys
import io
import contextlib
import traceback

def run_captured(code_str):
    # 创建字符串缓冲区
    stdout_capture = io.StringIO()
    stderr_capture = io.StringIO()
    
    try:
        # 使用 contextlib 重定向输出，这是线程安全的做法
        with contextlib.redirect_stdout(stdout_capture), contextlib.redirect_stderr(stderr_capture):
            try:
                # 创建独立的执行命名空间，避免污染全局环境
                exec_globals = {}
                exec(code_str, exec_globals)
            except Exception:
                # 捕获执行时的异常并打印到 stderr 缓冲区
                traceback.print_exc(file=stderr_capture)
    except Exception as e:
        # 捕获重定向本身的异常（极少发生）
        return "System Error", str(e)

    # 获取结果
    output = stdout_capture.getvalue()
    error = stderr_capture.getvalue()
    
    return output, error
            """

            // 1. 获取内置模块并执行我们的包装函数定义
            val mainModule = py.getModule("__main__")
            val builtins = py.getBuiltins()
            // 显式传入 globals 字典以避免 SystemError: frame does not exist
            builtins.callAttr("exec", wrapperCode, mainModule["__dict__"])

            // 2. 获取刚才定义的 run_captured 函数
            val runCaptured = mainModule["run_captured"]

            // 3. 调用该函数并传入用户代码
            val resultTuple = runCaptured?.call(code)

            // 4. 解析返回的元组 (output, error)
            val outputList = resultTuple?.asList()
            val output = outputList?.get(0).toString()
            val error = outputList?.get(1).toString()

            // 5. 格式化最终输出
            if (error.isNotEmpty()) {
                if (output.isNotEmpty()) {
                    "$output\n\n=== Errors ===\n$error"
                } else {
                    "=== Errors ===\n$error"
                }
            } else {
                if (output.isEmpty()) "Code executed successfully (no output)" else output
            }

        } catch (e: Exception) {
            e.printStackTrace()
            "Java/Kotlin System Error: ${e.message}"
        }
    }
}
