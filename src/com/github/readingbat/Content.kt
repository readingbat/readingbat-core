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

    java {
      repoRoot = "https://github.com/readingbat/readingbat-java-content"

      +remoteContent(repo = "readingbat-java-content").java.findChallengeGroup("Warmup 1")

      group("Warmup 1") {
        packageName = "warmup1"
        description = "This is a description of Warmup 1"

        challenge("JoinEnds") {
          description = "This is a description of joinEnds()"
          codingBatEquiv = "p141494"

          "Blue Zebra" returns "aB"
          "Tree" returns "eT"
          "Re" returns "eR"
          "p" returns "p"
          "" returns ""
        }
      }

      group("Warmup 2") {
        packageName = "warmup2"
        description = "This is a description of Warmup 2"
      }

      group("Logic 1") {
        packageName = "logic1"
        description = "This is a description of Logic 1"
      }

      group("Logic 2") {
        packageName = "logic2"
        description = "This is a description of Logic 2"
      }

      group("String 1") {
        packageName = "string1"
        description = "This is a description of String 1"
      }

      group("String 2") {
        packageName = "string2"
        description = "This is a description of String 2"
      }

      group("Array 1") {
        packageName = "array1"
        description = "This is a description of Array 1"
      }

      group("Array 2") {
        packageName = "array2"
        description = "This is a description of Array 2"
      }
    }

    python {
      repoRoot = "https://github.com/readingbat/readingbat-python-content"

      group("Warmup 1") {
        packageName = "warmup1"
        description = "This is a description of Warmup 1"

        challenge("simple_choice1") {

          true returns false
          false returns false
        }

        challenge("simple_choice2") {
          codingBatEquiv = "p173401"

          listOf(true, true) returns true
          listOf(true, false) returns true
          listOf(false, true) returns false
          listOf(false, false) returns true
        }
      }

      group("Warmup 2") {
        packageName = "warmup2"
        description = "This is a description of Warmup 2"
      }

      group("Logic 1") {
        packageName = "logic1"
        description = "This is a description of Logic 1"
      }
      group("Logic 2") {
        packageName = "logic2"
        description = "This is a description of Logic 2"
      }
      group("String 1") {
        packageName = "string1"
        description = "This is a description of String 1"
      }
      group("String 2") {
        packageName = "string2"
        description = "This is a description of String 2"
      }
      group("Array 1") {
        packageName = "array1"
        description = "This is a description of Array 1"
      }
      group("Array 2") {
        packageName = "array2"
        description = "This is a description of Array 2"
      }
    }
  }

/*
  readingBatContent {

    python {
      repoRoot = "https://github.com/readingbat/readingbat-python-content"

      group("Warmup 1") {
        packageName = "warmup1"
        description = "This is a description of Warmup 1"

        challenge("pythonFrontBack") {
          fileName = "front_back.py"

          "this is a test" returns "tt"
          "" returns ""
          "f" returns "f"
          "fg" returns "fg"
        }

        challenge("pythonFrontBackBool") {
          fileName = "front_back.py"

          "this is a test" returns true
          "this" returns false
        }
      }

      group("Warmup 2") {
        description = "This is a description of Warmup 2"

        challenge("pythonLongerMulti") {
          fileName = "front_back.py"
          description = """
              This is a description of front_back.py
            """

          listOf("thist", true, 4, 2.3) returns 5
        }

        challenge("pythonStringArray2") {
          fileName = "front_back.py"

          listOf(listOf("first", "second"), listOf(2, 4), listOf(true, false)) returns true
        }
      }

      group("Warmup 3") {
        description = "This is a description of Warmup 3"

        challenge("pythonStringArray3") {
          fileName = "FrontBack.py"

          listOf(listOf("first", "second"), listOf(2, 4), listOf(true, false)) returns true
        }
      }

      group("Logic 1") {}
      group("Logic 2") {}
      group("String 1") {}
      group("String 2") {}
      group("Array 1") {}
      group("Array 2") {}
    }

    java {
      repoRoot = "https://github.com/readingbat/readingbat-java-content"

      group("Warmup 1") {
        packageName = "warmup1"
        description = "This is a **description** of [Warmup 1](/python)"

        challenge("frontBack") {
          fileName = "FrontBack.java"
          description = """
              This is a **description** of *FrontBack.java* [Warmup 1](/java)
            """
          codingBatEquiv = "p171896"

          "this is a test" returns "tt"
          "" returns ""
          "f" returns "f"
          "fg" returns "fg"
        }

        challenge("frontBackBool") {
          fileName = "FrontBack.java"

          "this is a test" returns true
        }

        challenge("frontBackInt") {
          fileName = "FrontBack.java"

          "thistest8" returns false
          "thistest9" returns true
          "thistest10" returns true
        }

        challenge("frontBackDouble") {
          fileName = "FrontBack.java"

          "thistest8.7" returns 8.7
        }

        challenge("frontBackDouble2") {
          fileName = "FrontBack.java"

          "thistest8.7" returns 8.7
        }
      }
      group("Warmup 2") {
        packageName = "warmup2"

        challenge("longerMulti") {
          fileName = "FrontBack.java"

          listOf("this", false, 4, 2.3) returns 5
          listOf("this", true, 4, 2.3) returns 5
          listOf("this", false, 4, 2.3) returns 5
        }

        challenge("longerStringArray1") {
          fileName = "FrontBack.java"

          listOf(listOf("first", "second")) returns 2
          //listOf(listOf(true, false)) returns 2
        }

        challenge("longerStringArray2") {
          fileName = "FrontBack.java"

          listOf(listOf("first", "second"), listOf(2, 4), listOf(true, false)) returns 2
          //listOf(listOf("first", "second"), listOf("2", "4"), listOf(true, false)) returns 2
        }
      }

      group("Logic 1") {}
      group("Logic 2") {}
      group("String 1") {}
      group("String 2") {}
      group("Array 1") {}
      group("Array 2") {}
    }
  }
*/
