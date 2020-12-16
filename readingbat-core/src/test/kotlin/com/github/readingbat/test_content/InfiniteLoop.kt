package com.github.readingbat.com.github.readingbat.test_content

fun deadEnd(): Boolean {
  while (true) {
    println("I am stuck")
    Thread.sleep(1000)
  }
  return true
}

fun main() {
  println(deadEnd())
  println(deadEnd())
  println(deadEnd())
}