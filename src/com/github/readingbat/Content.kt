package com.github.readingbat

object Main {
  @JvmStatic
  fun main(args: Array<String>) {
    ReadingBatServer.start(content)
  }
}

val content =
  readingBatContent {

    +remoteContent(repo = "readingbat-java-content").java

    +remoteContent(repo = "readingbat-python-content").python

  }
