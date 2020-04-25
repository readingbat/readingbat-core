package com.github.readingbat

import com.github.pambrose.common.util.*
import com.github.readingbat.LanguageType.Java
import com.github.readingbat.LanguageType.Python
import com.github.readingbat.ReadingBatServer.userContent
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
    fun String.toLanguageType() = values().first { it.name.equals(this, ignoreCase = true) }
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
  private val languageList = listOf(
    LanguageGroup(Python),
    LanguageGroup(Java)
  )
  private val languageMap = languageList.map { it.languageType to it }.toMap()

  internal fun findLanguage(languageType: LanguageType) =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invalid language $languageType")

  internal fun validate() = languageList.forEach { it.validate() }

  fun python(block: LanguageGroup.() -> Unit) {
    findLanguage(Python).apply(block)
  }

  fun java(block: LanguageGroup.() -> Unit) {
    findLanguage(Java).apply(block)
  }
}

class LanguageGroup(internal val languageType: LanguageType) {
  private var srcPrefix = if (languageType.isJava()) "src/main/java" else "src" // default value
  internal val challengeGroups = mutableListOf<ChallengeGroup>()

  private val rawRoot by lazy { repoRoot.replace("github.com", "raw.githubusercontent.com") }
  internal val rawRepoRoot by lazy { listOf(rawRoot, "master", srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(repoRoot, "blob/master/", srcPrefix).toPath() }

  var repoRoot = ""

  internal fun hasChallengeGroup(groupName: String) = challengeGroups.any { it.name == groupName }

  internal fun findChallengeGroup(groupName: String) =
    groupName.decode()
      .let { decoded -> challengeGroups.firstOrNull { it.name == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  internal fun findChallenge(groupName: String, challengeName: String) =
    findChallengeGroup(groupName).findChallenge(challengeName)

  internal fun validate() {
    if (repoRoot.isEmpty())
      throw InvalidConfigurationException("${languageType.lowerName} language section is missing a repoRoot value")
  }

  fun group(name: String, block: ChallengeGroup.() -> Unit) {
    if (hasChallengeGroup(name))
      findChallengeGroup(name).apply(block)
    else
      challengeGroups += ChallengeGroup(this, name).apply(block)
  }
}

class ChallengeGroup(internal val languageGroup: LanguageGroup, internal val name: String) {
  internal val challenges = mutableListOf<AbstractChallenge>()
  internal val languageType = languageGroup.languageType
  internal val prefix = "${languageType.lowerName}/$name"

  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var packageName = ""
  var description = ""

  private fun hasChallenge(name: String) = challenges.any { it.name == name }

  internal fun findChallenge(name: String): AbstractChallenge =
    challenges.firstOrNull { it.name == name }
      ?: throw InvalidPathException("Challenge $prefix/$name not found.")

  fun challenge(name: String, block: AbstractChallenge.() -> Unit) {
    if (hasChallenge(name))
      throw InvalidConfigurationException("Challenge $prefix/$name already exists")

    val challenge = if (languageType == Java) JavaChallenge(this) else PythonChallenge(this)

    challenges += challenge.apply { this.name = name }.apply(block).apply { validate() }
  }
}

abstract class AbstractChallenge(private val group: ChallengeGroup) {
  private val paramSignature = mutableListOf<String>()
  private var multiArgTypes = ""
  private val challengeId = counter.incrementAndGet()
  internal val inputOutput = mutableListOf<Pair<String, String>>()
  internal val languageType = group.languageType
  internal val groupName = group.name
  internal val packageName = group.packageName

  private val fqName by lazy { packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { "${group.languageGroup.gitpodRoot}$fqName" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var name = ""
  var fileName = ""
  var codingBatEquiv = ""
  var description = ""

  internal abstract fun findFuncInfo(code: String): FuncInfo

  internal fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.languageGroup.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { "Fetching ${group.name.toDoubleQuoted()}/${fileName.toDoubleQuoted()} $path in $dur" }
        findFuncInfo(content)
      }

  internal fun validate() {
    if (name.isEmpty())
      throw InvalidConfigurationException("${name.toDoubleQuoted()} is empty")

    if (fileName.isEmpty())
      fileName = "$name.${languageType.suffix}"

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