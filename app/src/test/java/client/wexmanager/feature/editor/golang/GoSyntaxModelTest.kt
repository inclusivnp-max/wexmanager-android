package client.wexmanager.feature.editor.golang

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class GoSyntaxModelTest {
    @Test fun requestUsesUnsavedTextAndEscapesProtocolLines() {
        val source = "package main\nvar s = \"😀\\n\"\n"
        val request = syntaxRequest(42, "проект/main.go", source)
        assertFalse(request.contains('\n'))
        assertEquals(source, Json.parseToJsonElement(request).jsonObject["source"]!!.jsonPrimitive.content)
        assertEquals("проект/main.go", Json.parseToJsonElement(request).jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test fun acceptsCleanResultAndMultipleDiagnostics() {
        assertTrue(parseSyntaxResult("""{"id":7,"problems":[]}""",7,20).problems.isEmpty())
        val error = """{"start":3,"end":5,"line":1,"column":4,"message":"expected ')'"}"""
        val result = parseSyntaxResult("""{"id":8,"problems":[$error,$error]}""",8,20)
        assertEquals(1,result.problems.size)
        assertEquals(GoProblem(3,5,1,4,"expected ')'"),result.problems.single())
    }

    @Test fun rejectsStaleOrBrokenProtocol() {
        assertThrows(IllegalArgumentException::class.java) { parseSyntaxResult("""{"id":1,"problems":[]}""",2,20) }
        assertThrows(IllegalStateException::class.java) { parseSyntaxResult("""{"id":2}""",2,20) }
        assertThrows(Exception::class.java) { parseSyntaxResult("not-json",2,20) }
    }

    @Test fun backendErrorsNeverLookLikeCleanSyntax() {
        val error = assertThrows(IllegalStateException::class.java) { parseSyntaxResult("""{"id":1,"problems":[],"error":"parser unavailable"}""",1,20) }
        assertEquals("parser unavailable",error.message)
    }

    @Test fun refusesOutOfBufferRanges() {
        for ((start,end,line,column) in listOf(listOf(-1,2,1,1),listOf(1,30,1,1),listOf(5,2,1,1),listOf(0,1,0,1),listOf(0,1,1,0))) {
            assertThrows(IllegalArgumentException::class.java) {
                parseSyntaxResult("""{"id":1,"problems":[{"start":$start,"end":$end,"line":$line,"column":$column,"message":"bad"}]}""",1,20)
            }
        }
    }

    @Test fun revisionsIgnoreTypingTabSwitchAndPauseRaces() {
        val gate = GoSyntaxRevision()
        val a = gate.advance("a.go");val b = gate.advance("a.go");val c = gate.advance("b.go")
        assertFalse(gate.accepts(a,"a.go"));assertFalse(gate.accepts(b,"a.go"));assertTrue(gate.accepts(c,"b.go"))
        gate.advance("")
        assertFalse(gate.accepts(c,"b.go"))
    }

    @Test fun errorNavigationWrapsBothDirections() {
        val first = GoProblem(5,6,1,6,"first");val last = GoProblem(15,16,2,4,"last")
        val errors = listOf(last,first)
        assertEquals(first,nextGoProblem(errors,0,false))
        assertEquals(last,nextGoProblem(errors,5,false))
        assertEquals(first,nextGoProblem(errors,15,false))
        assertEquals(last,nextGoProblem(errors,0,true))
        assertEquals(first,nextGoProblem(errors,15,true))
        assertNull(nextGoProblem(emptyList(),0,false))
    }

    @Test fun realParserChecksBrokenAndFixedUnsavedBuffersInOneProcess() {
        val executable = System.getenv("WEX_GO_SYNTAX_HOST")?.let(::File)
        assumeTrue("Host parser required for protocol integration", executable?.canExecute() == true)
        val process = ProcessBuilder(executable!!.path).start()
        try {
            val input = process.outputStream.bufferedWriter()
            val output = process.inputStream.bufferedReader()
            val broken = "package main\nfunc main( {\n"
            input.write(syntaxRequest(1,"unsaved.go",broken));input.newLine();input.flush()
            assertTrue(parseSyntaxResult(output.readLine(),1,broken.length).problems.isNotEmpty())
            val fixed = "package main\nimport \"not.installed/module\"\nfunc main() { undefined() }\n"
            input.write(syntaxRequest(2,"unsaved.go",fixed));input.newLine();input.flush()
            assertTrue(parseSyntaxResult(output.readLine(),2,fixed.length).problems.isEmpty())
            input.close()
            assertTrue(process.waitFor(3,TimeUnit.SECONDS));assertEquals(0,process.exitValue())
        } finally { process.destroyForcibly() }
    }
}
