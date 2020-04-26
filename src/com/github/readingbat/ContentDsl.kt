package com.github.readingbat

import com.github.pambrose.common.script.KtsScript
import com.github.pambrose.common.util.*
import com.github.readingbat.Constants.github
import com.github.readingbat.Constants.githubUserContent
import com.github.readingbat.Content.Companion.remoteMap
import com.github.readingbat.LanguageType.Java
import com.github.readingbat.LanguageType.Python
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

@DslMarker
annotation class ReadingBatTagMarker

object ReadingBatServer {
  fun start(content: Content) {
    val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
    //val clargs = commandLineEnvironment(args.plus("port=$port"))
    embeddedServer(CIO, port = port) { module(content = content) }.start(wait = true)
  }
}

fun remoteContent(scheme: String = "https://",
                  domainName: String = github,
                  organization: String = "readingbat",
                  repo: String,
                  branch: String = "master",
                  srcPath: String = "src/main/kotlin",
                  fileName: String = "Content.kt",
                  variableName: String = "content"): Content {
  val p = scheme + domainName.replace(github, githubUserContent).ensureSuffix("/") +
      listOf(organization, repo, branch, srcPath).toPath() + fileName.ensureSuffix(".kt")
  return remoteMap.computeIfAbsent(p) { path ->
    val (text, dur) = measureTimedValue { URL(path).readText() }

    val importDecl = "import com.github.readingbat.readingBatContent"

    val code =
      (if (text.contains(importDecl)) "" else importDecl) + """
      $text
      $variableName
      """

    val content = KtsScript().run { eval(code) as Content }
    content.validate()
    println("In $dur got $content")
    content
  }
}


fun readingBatContent(block: Content.() -> Unit) = Content().apply(block).apply { validate() }

@ReadingBatTagMarker
class Content {
  val java = LanguageGroup(Java)
  val python = LanguageGroup(Python)

  private val languageList = listOf(java, python)
  private val languageMap = languageList.map { it.languageType to it }.toMap()

  internal fun findLanguage(languageType: LanguageType) =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invalid language $languageType")

  internal fun validate() = languageList.forEach { it.validate() }

  @ReadingBatTagMarker
  fun Content.java(block: LanguageGroup.() -> Unit) {
    findLanguage(Java).apply(block)
  }

  @ReadingBatTagMarker
  fun python(block: LanguageGroup.() -> Unit) {
    findLanguage(Python).apply(block)
  }


  @ReadingBatTagMarker
  operator fun LanguageGroup.unaryPlus(): Unit {
    val languageGroup = this@Content.findLanguage(this.languageType)
    challengeGroups.forEach { languageGroup.addGroup(it) }
  }

  override fun toString() = "Content(languageList=$languageList)"

  companion object {
    internal val remoteMap = mutableMapOf<String, Content>()
  }
}

@ReadingBatTagMarker
class LanguageGroup(internal val languageType: LanguageType) {
  private var localGroupCount = 0

  private var srcPrefix = if (languageType.isJava()) "src/main/java" else "python" // default value
  val challengeGroups = mutableListOf<ChallengeGroup>()

  private val rawRoot by lazy { repoRoot.replace(github, githubUserContent) }
  internal val rawRepoRoot by lazy { listOf(rawRoot, "master", srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(repoRoot, "blob/master/", srcPrefix).toPath() }

  var repoRoot = ""

  internal fun hasGroup(groupName: String) = challengeGroups.any { it.name == groupName }

  fun addGroup(group: ChallengeGroup) {
    if (hasGroup(group.name))
      throw InvalidConfigurationException("Duplicate group name: ${group.name}")
    challengeGroups += group
  }

  fun findGroup(groupName: String) =
    groupName.decode()
      .let { decoded -> challengeGroups.firstOrNull { it.name == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  internal fun findChallenge(groupName: String, challengeName: String) =
    findGroup(groupName).findChallenge(challengeName)

  internal fun validate() {
    if (localGroupCount > 0 && repoRoot.isEmpty())
      throw InvalidConfigurationException("${languageType.lowerName} section is missing a repoRoot value")
  }

  @ReadingBatTagMarker
  operator fun ChallengeGroup.unaryPlus(): Unit {
    this@LanguageGroup.addGroup(this)
  }

  @ReadingBatTagMarker
  fun group(name: String, block: ChallengeGroup.() -> Unit) {
    if (hasGroup(name))
      throw InvalidConfigurationException("Duplicate group name: $name")
    challengeGroups += ChallengeGroup(this, name).apply(block)
    localGroupCount++
  }

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPrefix='$srcPrefix', challengeGroups=$challengeGroups, repoRoot='$repoRoot')"

}

@ReadingBatTagMarker
class ChallengeGroup(internal val languageGroup: LanguageGroup, internal val name: String) {
  internal val languageType = languageGroup.languageType
  internal val challenges = mutableListOf<AbstractChallenge>()
  internal val prefix = "${languageType.lowerName}/$name"

  val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var packageName = ""
  var description = ""

  fun hasChallenge(name: String) = challenges.any { it.name == name }

  fun findChallenge(name: String): AbstractChallenge =
    challenges.firstOrNull { it.name == name }
      ?: throw InvalidPathException("Challenge $prefix/$name not found.")

  @ReadingBatTagMarker
  operator fun AbstractChallenge.unaryPlus(): Unit {
    if (this@ChallengeGroup.hasChallenge(name))
      throw InvalidConfigurationException("Duplicate challenge name: $name")
    this@ChallengeGroup.challenges += this
  }

  @ReadingBatTagMarker
  fun challenge(name: String, block: AbstractChallenge.() -> Unit) {
    if (hasChallenge(name))
      throw InvalidConfigurationException("Challenge $prefix/$name already exists")
    val challenge = if (languageType == Java) JavaChallenge(this) else PythonChallenge(this)
    challenges += challenge.apply { this.name = name }.apply(block).apply { validate() }
  }

  override fun toString() = "ChallengeGroup(name='$name', challenges=$challenges, packageName='$packageName')"
}

@ReadingBatTagMarker
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

  fun validate() {
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
            set.joinToString(" and ") { "($it)" })
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

  override fun toString() = "AbstractChallenge(packageName='$packageName', fileName='$fileName')"

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

fun List<String>.toPath() = joinToString("") { it.ensureSuffix("/") }