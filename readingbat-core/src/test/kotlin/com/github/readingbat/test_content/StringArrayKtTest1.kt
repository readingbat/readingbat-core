package com.github.readingbat.com.github.readingbat.test_content

fun combine(s1: String, s2: String): Array<String> {
  return arrayOf(s1, s2)
}

fun main() {
  println(combine("Car", "wash"))
  println(combine("Hello", " world"))
  println(combine("", ""))
}
