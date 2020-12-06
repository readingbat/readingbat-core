package com.github.readingbat.com.github.readingbat.test_content

fun deadEnd(i: Int): Boolean {
  while (true) {
    println("I am stuck")
    Thread.sleep(1000)
  }
  return true
}

fun main() {
  println(deadEnd(1))
  println(deadEnd(2))
  println(deadEnd(3))
}