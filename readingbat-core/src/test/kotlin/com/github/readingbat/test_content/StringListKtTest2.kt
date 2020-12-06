package com.github.readingbat.com.github.readingbat.test_content

fun combine2l(strs: List<String>): List<String> {
  val retval = mutableListOf<String>()
  for (s in strs) retval += s.toUpperCase()
  return retval
}

fun main() {
  println(combine2l(listOf("Car", "wash")))
  println(combine2l(listOf("Hello", " world")))
  println(combine2l(listOf("Hello")))
  println(combine2l(listOf()))
}
