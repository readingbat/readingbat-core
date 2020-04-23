package com.github.pambrose.readingbat


object Config {

  @JvmStatic
  fun main(args: Array<String>) {

    configuration {

      python {
        repoRoot = "https://github.com/readingbat/readingbat-python-content"

        group("Warm-up 1") {
          description = "This is a description of Warm-up 1"

          challenge("pythonFrontBack") {
            fileName = "FrontBack.py"
            funcName = "frontBack"

            "this is a test" returns "tt"
            "" returns ""
            "f" returns "f"
            "fg" returns "fg"
          }

          challenge("pythonFrontBackBool") {
            fileName = "FrontBack.py"
            funcName = "frontBackBool"

            "this is a test" returns true
            "this" returns false
          }
        }

        group("Warm-up 2") {
          description = "This is a description of Warm-up 2"

          challenge("pythonLongerMulti") {
            fileName = "FrontBack.py"
            description = """
          
        """.trimIndent()

            listOf("thist", true, 4, 2.3) returns 5
          }

          challenge("pythonStringArray2") {
            fileName = "FrontBack.py"

            listOf(listOf("first", "second"), listOf(2, 4), listOf(true, false)) returns true
          }
        }

        group("Warm-up 3") {
          description = "This is a description of Warm-up 3"

          challenge("pythonStringArray3") {
            fileName = "FrontBack.py"

            listOf(listOf("first", "second"), listOf(2, 4), listOf(true, false)) returns true
          }
        }
      }

      java {
        repoRoot = "https://github.com/readingbat/readingbat-java-content"

        group("Warm-up 1") {
          description = "This is a description a description a description of Warm-up 1"
          packageName = "warmup1"

          challenge("frontBack") {
            fileName = "FrontBack.java"
            funcName = "frontBack"
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
            funcName = "frontBackDouble"

            "thistest8.7" returns 8.7
          }

          challenge("frontBackDouble2") {
            fileName = "FrontBack.java"
            funcName = "frontBackDouble2"

            "thistest8.7" returns 8.7
          }
        }

        group("Warm-up 2") {
          packageName = "warmup2"

          challenge("longerMulti") {
            fileName = "FrontBack.java"
            funcName = "longerMulti"

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

        group("Warm-up 3") {}
        group("Warm-up 4") {}
        group("Warm-up 5") {}
        group("Warm-up 6") {}
        group("Warm-up 7") {}
      }
    }
  }
}
