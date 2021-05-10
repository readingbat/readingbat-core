package com.github.readingbat.com.github.readingbat.test_content

import java.util.*

fun combine2(strs: Array<String>): Array<String> {
  val retval = arrayOf(*strs)
  for (i in strs.indices) retval[i] = strs[i].uppercase(Locale.getDefault())
  return retval
}

fun main() {
  println(combine2(arrayOf("Car", "wash")))
  println(combine2(arrayOf("Hello", " world")))
  println(combine2(arrayOf("Hello")))
  println(combine2(arrayOf()))
}
