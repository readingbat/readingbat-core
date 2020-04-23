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

enum class LanguageType(val useDoubleQuotes: Boolean) {
  Java(true), Python(false);

  val lcname get() = name.toLowerCase()

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

    @JvmStatic
    fun main(arg: Array<String>) {
      val s = """
public class FrontBack {

    public static String frontBack(String str) {
        if (str.length() < 2)
            return str;

        String b = str.substring(0, 1);
        String e = str.substring(str.length() - 1, str.length());
        String m = str.substring(1, str.length() - 1);

        return e + m + b;
    }

    public static void main(String[] args) {

        System.out.println(frontBack("This is a test"));
        System.out.println(frontBack(""));
    }
}

      """
      println(findJavaFunction(s))
    }

  }
}

class LanguageGroup(internal val languageType: LanguageType) {
  internal val challengeGroups = mutableListOf<ChallengeGroup>()
  internal val rawRepoRoot
    get() = "${repoRoot.ensureSuffix("/")}master/${srcPrefix.ensureSuffix("/")}".replace(
      "github.com",
      "raw.githubusercontent.com"
    )
  internal val srcRoot get() = repoRoot.ensureSuffix("/") + srcPrefix.ensureSuffix("/")
  internal val gitpodRoot get() = repoRoot.ensureSuffix("/") + "blob/master/" + srcPrefix.ensureSuffix("/")

  var repoRoot = ""
  var srcPrefix = "src/main/java"  // default value

  private fun contains(name: String) = challengeGroups.any { it.name == name }
  internal fun find(name: String) = name.decode().let { decoded -> challengeGroups.first { it.name == decoded } }

  fun group(name: String, block: ChallengeGroup.() -> Unit) {
    if (contains(name))
      throw InvalidConfigurationException("Group ${name.toDoubleQuoted()} already exists")
    challengeGroups += ChallengeGroup(this, name).apply(block)
  }

  internal fun validate() {
    if (repoRoot.isEmpty())
      throw InvalidConfigurationException("${languageType.lcname} block is missing a repoRoot value")
  }
}

class ChallengeGroup(internal val language: LanguageGroup, internal val name: String) {
  internal val challenges = mutableListOf<AbstractChallenge>()
  var description = ""
  var packageName = ""

  private fun contains(name: String) = challenges.any { it.name == name }

  fun challenge(name: String, block: AbstractChallenge.() -> Unit) {
    val challenge =
      (if (language.languageType == Java) JavaChallenge(this) else PythonChallenge(
        this
      )).apply {
        this.name = name
        this.funcName = name  // Default funcName to name
      }.apply(block)
    challenge.validate()

    if (contains(name))
      throw InvalidConfigurationException("Challenge ${name.toDoubleQuoted()} already exists")
    challenges += challenge
  }
}

fun findJavaFunction(code: String): String {
  val lines = code.split("\n")
  val staticLineNums =
    lines.mapIndexed { i, code -> i to code }
      .filter { it.second.contains(Regex(".*static.*\\(")) }
      .map { it.first }

  return lines.subList(staticLineNums.first(), staticLineNums.last() - 1).joinToString("\n").trimIndent()
}

abstract class AbstractChallenge(internal val group: ChallengeGroup) {
  protected val challengeId = counter.incrementAndGet()
  private val paramSignature = mutableListOf<String>()
  internal val inputOutput = mutableListOf<Pair<String, String>>()
  internal val languageType get() = group.language.languageType
  private var multiArgTypes = ""
  val githubUrl get() = "${group.language.srcRoot}$fqName"
  val gitpodUrl get() = "${group.language.gitpodRoot}$fqName"

  var name: String = ""
  var fileName: String = ""
  var funcName: String = ""
  var codingBatEquiv = ""
  var description: String = ""

  val fqName get() = "${group.packageName.ensureSuffix("/")}${fileName.ensureSuffix(".${languageType.lcname}")}"

  abstract fun funcText(): String

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

  fun validate() {
    if (fileName.isEmpty()) throw InvalidConfigurationException("${name.toDoubleQuoted()} is missing filename")

    if (multiArgTypes.isNotEmpty())
      throw InvalidConfigurationException("${name.toDoubleQuoted()} has $multiArgTypes")

    val set = paramSignature.toSet()
    if (set.size > 1)
      throw InvalidConfigurationException("${name.toDoubleQuoted()} has inconsistent function arguments: " +
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

  companion object {
    val counter = AtomicInteger(0)
    val sourcesMap = ConcurrentHashMap<Int, String>()
  }
}

class PythonChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun funcText(): String {
    return """
    def pos_neg(a, b, negative):
      if negative:
        return (a < 0 and b < 0)
      else:
        return ((a < 0 and b > 0) or (a > 0 and b < 0))
              """.trimIndent()
  }

  companion object : KLogging()
}

class JavaChallenge(group: ChallengeGroup) : AbstractChallenge(group) {
  override fun funcText() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.language.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { "Fetching ${group.name.toDoubleQuoted()}/${funcName.toDoubleQuoted()} $path in $dur" }
        findJavaFunction(content)
      }

  companion object : KLogging()
}

class InvalidConfigurationException(msg: String) : Exception(msg)
