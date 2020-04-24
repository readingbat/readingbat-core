package com.github.pambrose.readingbat

import com.github.pambrose.common.util.*
import com.github.pambrose.readingbat.LanguageType.Java
import com.github.pambrose.readingbat.LanguageType.Python
import com.github.pambrose.readingbat.ReadingBatServer.userContent
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

enum class LanguageType(val useDoubleQuotes: Boolean, val suffix: String) {
  Java(true, "java"), Python(false, "py");

  val lowerName = name.toLowerCase()

  fun isJava() = this == Java
  fun isPython() = this == Python

  companion object {
    fun String.asLanguageType() = values().first { it.name.equals(this, ignoreCase = true) }
  }
}

object ReadingBatServer {
  internal val userContent = Content()

  fun start(content: Content) {
    content.validate()
    val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
    //val clargs = commandLineEnvironment(args.plus("port=$port"))
    val httpServer = embeddedServer(CIO, port = port) { module(content = userContent) }
    httpServer.start(wait = true)

  }
}

fun readingBatContent(block: Content.() -> Unit) = userContent.apply(block)

class Content {
  private val languageList = listOf(LanguageGroup(Python), LanguageGroup(Java))
  private val languageMap = languageList.map { it.languageType to it }.toMap()

  internal fun getLanguage(languageType: LanguageType) =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invaid language $languageType")

  fun python(block: LanguageGroup.() -> Unit) {
    getLanguage(Python).apply(block)
  }

  fun java(block: LanguageGroup.() -> Unit) {
    getLanguage(Java).apply(block)
  }

  fun validate() = languageList.forEach { it.validate() }
}

class LanguageGroup(internal val languageType: LanguageType) {
  private var srcPrefix = if (languageType.isJava()) "src/main/java" else "src" // default value
  internal val challengeGroups = mutableListOf<ChallengeGroup>()

  private val rawRoot by lazy { repoRoot.replace("github.com", "raw.githubusercontent.com") }
  internal val rawRepoRoot by lazy { listOf(rawRoot, "master", srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(repoRoot, "blob/master/", srcPrefix).toPath() }

  var repoRoot = ""

  private fun contains(name: String) = challengeGroups.any { it.name == name }
  internal fun find(name: String) = name.decode().let { decoded -> challengeGroups.first { it.name == decoded } }

  fun group(name: String, block: ChallengeGroup.() -> Unit) {
    if (contains(name))
      throw InvalidConfigurationException("Group ${name.toDoubleQuoted()} already exists")
    challengeGroups += ChallengeGroup(this, name).apply(block)
  }

  internal fun validate() {
    if (repoRoot.isEmpty())
      throw InvalidConfigurationException("${languageType.lowerName} block is missing a repoRoot value")
  }
}

class ChallengeGroup(internal val language: LanguageGroup, internal val name: String) {
  internal val challenges = mutableListOf<AbstractChallenge>()
  var packageName = ""
  var description = ""

  val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  private fun contains(name: String) = challenges.any { it.name == name }

  fun challenge(name: String, block: AbstractChallenge.() -> Unit) {
    val challenge =
      (if (language.languageType == Java) JavaChallenge(this) else PythonChallenge(this)).apply {
        this.name = name
      }.apply(block)
    challenge.validate()

    if (contains(name))
      throw InvalidConfigurationException("Challenge ${name.toDoubleQuoted()} already exists")
    challenges += challenge
  }
}

abstract class AbstractChallenge(private val group: ChallengeGroup) {
  private val paramSignature = mutableListOf<String>()
  private var multiArgTypes = ""
  private val challengeId = counter.incrementAndGet()
  internal val inputOutput = mutableListOf<Pair<String, String>>()
  internal val languageType = group.language.languageType

  private val fqName by lazy { group.packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { "${group.language.gitpodRoot}$fqName" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var name: String = ""
  var fileName: String = ""
  var codingBatEquiv = ""
  var description: String = ""


  abstract fun findFuncInfo(code: String): FuncInfo

  fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.language.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { "Fetching ${group.name.toDoubleQuoted()}/${fileName.toDoubleQuoted()} $path in $dur" }
        findFuncInfo(content)
      }

  fun validate() {
    if (fileName.isEmpty()) throw InvalidConfigurationException("${name.toDoubleQuoted()} is missing filename")

    if (multiArgTypes.isNotEmpty())
      throw InvalidConfigurationException("${name.toDoubleQuoted()} has $multiArgTypes")

    val set = paramSignature.toSet()
    if (set.size > 1)
      throw InvalidConfigurationException(
        "${name.toDoubleQuoted()} has inconsistent function arguments: " +
            set.joinToString(" and ") { "($it)" }
      )
  }

  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    if (this is String)
      if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
    else if (capitalizePythonBooleans && this is Boolean && languageType == Python)
      toString().capitalize()
    else
      toString()

  private fun List<*>.toQuotedStrings() = "[${map { it.prettyQuote() }.toCsv()}]"

  private fun processArrayTypes(types: List<String>, arg: List<*>) {
    val typesSet = types.toSet()
    if (typesSet.size > 1)
      multiArgTypes = "array values with inconsistent types: $typesSet in array ${arg.toQuotedStrings()}"
  }

  infix fun <A : Any, B : Any> A.returns(solution: B) {
    inputOutput.add(prettyQuote() to solution.prettyQuote(useDoubleQuotes = true))
    paramSignature.add(simpleClassName)
  }

  infix fun <A : Any, B : Any> List<A>.returns(solution: B) {
    val argTypes = mutableListOf<String>()
    val args =
      this.map { arg ->
        when (arg) {
          is List<*> -> {
            val arrayTypes = mutableListOf<String>()
            val quotedVals =
              arg.map {
                arrayTypes += it?.simpleClassName ?: "Null"
                it.prettyQuote()
              }
            processArrayTypes(arrayTypes, arg)
            argTypes += "[${arrayTypes.toCsv()}]"
            quotedVals.toString()
          }
          else -> {
            argTypes += arg.simpleClassName
            arg.prettyQuote()
          }
        }
      }.toCsv()

    inputOutput.add(args to solution.prettyQuote(useDoubleQuotes = true))
    paramSignature.add(argTypes.toCsv())
  }

  companion object : KLogging() {
    val counter = AtomicInteger(0)
    val sourcesMap = ConcurrentHashMap<Int, FuncInfo>()
  }
}

class PythonChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun findFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcName = lines[lineNums.first()].substringAfter("def ").substringBefore("(").trim()
    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FuncInfo(funcName, funcCode)
  }
}

class JavaChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun findFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("static.*\\(")) }
        .map { it.first }

    val funcName = lines[lineNums.first()].substringAfter("static ").substringBefore("(").split(" ")[1].trim()
    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FuncInfo(funcName, funcCode)
  }
}

class FuncInfo(val name: String, val code: String)

class InvalidConfigurationException(msg: String) : Exception(msg)

private fun List<String>.toPath() = joinToString("") { it.ensureSuffix("/") }
