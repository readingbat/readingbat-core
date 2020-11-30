package com.github.readingbat.test_content

fun combinel(s1: String, s2: String): List<String> {
  return listOf(s1, s2)
}

fun main() {
  println(combinel("Car", "wash"))
  println(combinel("Hello", " world"))
  println(combinel("", ""))
}
