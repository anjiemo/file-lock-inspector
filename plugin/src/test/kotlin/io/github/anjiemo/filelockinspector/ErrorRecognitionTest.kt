package io.github.anjiemo.filelockinspector

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ErrorRecognitionTest {
    @Test fun `extract Chinese failure without interpreting numeric prefix as PID`() {
        assertEquals(listOf("D:\\项目 space\\app\\classes.jar"), ErrorRecognition.paths("1800648645D:\\项目 space\\app\\classes.jar: 另一个程序正在使用此文件，进程无法访问。"))
    }
    @Test fun `extract English failure and remove wrapping punctuation`() {
        assertEquals(listOf("C:\\project\\classes.jar"), ErrorRecognition.paths("java.nio.file.FileSystemException: C:\\project\\classes.jar: The process cannot access the file because it is being used by another process"))
    }
    @Test fun `do not classify access denied or unrelated lines as sharing violations`() {
        assertTrue(ErrorRecognition.paths("C:\\project\\classes.jar: Access is denied").isEmpty())
        assertTrue(ErrorRecognition.paths("C:\\project\\classes.jar\nOther file is being used by another process").isEmpty())
    }
    @Test fun `deduplicate repeated errors`() {
        val error = "C:\\project\\classes.jar: sharing violation"
        assertEquals(1, ErrorRecognition.paths("$error\n$error").size)
    }
}
