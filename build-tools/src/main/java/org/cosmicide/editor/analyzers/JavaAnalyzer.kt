package org.cosmicide.editor.analyzers

import com.sun.tools.javac.api.JavacTool
import com.sun.tools.javac.file.JavacFileManager
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.widget.CodeEditor
import org.cosmicide.project.Project
import org.cosmicide.rewrite.common.Prefs
import org.cosmicide.rewrite.util.FileUtil
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation

class JavaAnalyzer(
    val editor: CodeEditor,
    val project: Project,
    val currentFile: File
) {
    private var diagnostics = DiagnosticCollector<JavaFileObject>()
    private val tool: JavacTool by lazy { JavacTool.create() }
    private val standardFileManager: JavacFileManager by lazy {
        tool.getStandardFileManager(
            diagnostics, Locale.getDefault(), Charset.defaultCharset()
        )
    }
    private val reusableCompiler by lazy {
        ReusableCompiler()
    }

    init {
        standardFileManager.setLocation(
            StandardLocation.PLATFORM_CLASS_PATH,
            FileUtil.classpathDir.walk().filter { it.extension == "jar" }.toList()
        )
        if (!project.binDir.exists()) {
            project.binDir.mkdirs()
        }
        standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(project.binDir))
    }

    fun analyze() {
        val version = Prefs.compilerJavaVersion
        val toCompile = getSourceFiles()

        with(standardFileManager) {
            setLocation(StandardLocation.CLASS_PATH, getClasspath())
            autoClose = false
        }

        val args = mutableListOf<String>()

        args.add("-proc:none")
        args.add("-source")
        args.add(version.toString())
        args.add("-target")
        args.add(version.toString())

        /*val borrow =
            reusableCompiler.getTask(
                standardFileManager,
                diagnostics,
                args,
                null,
                toCompile
            )*/

        tool.getTask(null, standardFileManager, diagnostics, args, null, toCompile).apply {
            parse()
            analyze()
            if (diagnostics.diagnostics.isEmpty()) {
                generate().forEach(Cache::saveCache)
            }
        }
    }


    fun reset() {
        diagnostics = DiagnosticCollector<JavaFileObject>()
    }

    fun getDiagnostics(): List<DiagnosticRegion> {
        val diagnostic = diagnostics.diagnostics
        val problems = mutableListOf<DiagnosticRegion>()
        for (it in diagnostic) {
            if (it.source == null) continue
            val severity =
                if (it.kind == Diagnostic.Kind.ERROR) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING
            problems.add(
                DiagnosticRegion(
                    it.startPosition.toInt(),
                    it.endPosition.toInt(),
                    severity,
                    0,
                    DiagnosticDetail(it.getMessage(Locale.getDefault()))
                )
            )
        }
        return problems
    }

    private fun getClasspath(): List<File> {
        val classpath = mutableListOf<File>()
        classpath.add(File(project.binDir, "classes"))
        project.libDir.walk().forEach {
            if (it.extension == "jar") {
                classpath.add(it)
            }
        }


        project.binDir
            .resolve("classes")
            .walk()
            .filter { it.extension == "class" }
            .forEach {
                if (Cache.getCache(it) != null && Cache.getCache(it)!!.lastModified == it.lastModified()) {
                    classpath.add(it)
                }
            }

        return classpath
    }

    private fun getSourceFiles(): List<JavaFileObject> {
        val sourceFiles = mutableListOf<JavaFileObject>()

        project.srcDir.invoke().walk().forEach {
            if (it.extension == "java") {
                val cache = Cache.getCache(it)
                if (cache == null || cache.lastModified < it.lastModified()) {
                    sourceFiles.add(Cache.saveCache(it))
                }
            }
        }

        return sourceFiles
    }
}