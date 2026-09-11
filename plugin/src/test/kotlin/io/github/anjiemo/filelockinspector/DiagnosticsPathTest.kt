package io.github.anjiemo.filelockinspector

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DiagnosticsPathTest {
    @Test fun `snapshot keys match Windows spelling variants`() {
        assertEquals(Diagnostics.pathKey("D:\\Project\\build\\classes.jar"),
            Diagnostics.pathKey("d:/project/build/./classes.jar"))
        assertEquals(Diagnostics.pathKey("D:\\Project\\build\\classes.jar"),
            Diagnostics.pathKey("D:/Project/temp/../build/classes.jar"))
    }

    @Test fun `different targets retain different snapshot keys`() {
        assertNotEquals(Diagnostics.pathKey("D:/project/classes.jar"),
            Diagnostics.pathKey("D:/other/classes.jar"))
    }
}
