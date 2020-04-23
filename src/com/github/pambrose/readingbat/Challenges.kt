package com.github.pambrose.readingbat

import com.github.pambrose.common.util.*
import com.github.pambrose.readingbat.Configuration.Companion.config
import com.github.pambrose.readingbat.LanguageType.Java
import com.github.pambrose.readingbat.LanguageType.Python
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
}

fun fromPath(s: String) = s.capitalize().let { cap -> LanguageType.values().first { it.name == cap } }

fun configuration(start: Boolean = true, block: Configuration.() -> Unit): Configuration {
  config = Configuration().apply(block)

  if (start) {
    val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
    val httpServer = embeddedServer(CIO, port = port) { module(config = config) }
    httpServer.start(wait = true)
  }

  return config
}

class Configuration {
  private val javaLanguageGroup = LanguageGroup(Java)
  private val pythonLanguageGroup = LanguageGroup(Python)

  internal fun forLanguage(languageType: LanguageType) =
    when (languageType) {
      Python -> pythonLanguageGroup
      else -> javaLanguageGroup
    }

  fun python(block: LanguageGroup.() -> Unit) {
    pythonLanguageGroup.apply {
      apply(block)
      validate()
    }
  }

  fun java(block: LanguageGroup.() -> Unit) {
    javaLanguageGroup.apply {
      apply(block)
      validate()
    }
  }

  companion object {
    internal lateinit var config: Configuration
  }
}

class LanguageGroup(internal val languageType: LanguageType) {
  private val rawRoot by lazy { repoRoot.replace("github.com", "raw.githubusercontent.com") }
  internal val challengeGroups = mutableListOf<ChallengeGroup>()
  internal val rawRepoRoot by lazy { listOf(rawRoot, "master", srcPrefix).toPath() }
  internal val srcRoot by lazy { listOf(repoRoot, srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(repoRoot, "blob/master/", srcPrefix).toPath() }

  var repoRoot = ""
  var srcPrefix = if (languageType.isJava()) "src/main/java" else "src" // default value

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
  var description = ""
  var packageName = ""

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

abstract class AbstractChallenge(internal val group: ChallengeGroup) {
  private val paramSignature = mutableListOf<String>()
  private var multiArgTypes = ""
  protected val challengeId = counter.incrementAndGet()
  internal val inputOutput = mutableListOf<Pair<String, String>>()
  internal val languageType = group.language.languageType
  internal val fqName by lazy { group.packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { "${group.language.gitpodRoot}$fqName" }

  var name: String = ""
  var fileName: String = ""
  var codingBatEquiv = ""
  var description: String = ""

  abstract fun findFuncInfo(code: String): FunInfo

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
    val sourcesMap = ConcurrentHashMap<Int, FunInfo>()
  }
}

class PythonChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun findFuncInfo(code: String): FunInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, code -> i to code }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcName = lines[lineNums.first()].substringAfter("def ").substringBefore("(").trim()
    val code = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FunInfo(funcName, code)
  }
}

class JavaChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun findFuncInfo(code: String): FunInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, code -> i to code }
        .filter { it.second.contains(Regex("static.*\\(")) }
        .map { it.first }

    val funcName = lines[lineNums.first()].substringAfter("static ").substringBefore("(").split(" ")[1].trim()
    val code = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FunInfo(funcName, code)
  }
}

class FunInfo(val name: String, val code: String)

class InvalidConfigurationException(msg: String) : Exception(msg)

fun List<String>.toPath() = this.map { it.ensureSuffix("/") }.joinToString("")
