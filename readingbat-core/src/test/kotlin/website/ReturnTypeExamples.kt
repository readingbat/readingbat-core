@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.readingbat.dsl.ReturnType.BooleanArrayType
import com.readingbat.dsl.ReturnType.BooleanListType
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.CharType
import com.readingbat.dsl.ReturnType.FloatArrayType
import com.readingbat.dsl.ReturnType.FloatListType
import com.readingbat.dsl.ReturnType.FloatType
import com.readingbat.dsl.ReturnType.IntArrayType
import com.readingbat.dsl.ReturnType.IntListType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.StringArrayType
import com.readingbat.dsl.ReturnType.StringListType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.readingBatContent

// --8<-- [start:scalar_types]
val scalarTypeExamples =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      group("Scalar-Types") {
        packageName = "scalars"

        challenge("isPositive") { returnType = BooleanType }
        challenge("doubleValue") { returnType = IntType }
        challenge("average") { returnType = FloatType }
        challenge("greet") { returnType = StringType }
        challenge("firstChar") { returnType = CharType }
      }
    }
  }
// --8<-- [end:scalar_types]

// --8<-- [start:array_types]
val arrayTypeExamples =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      group("Array-Types") {
        packageName = "arrays"

        challenge("boolFlags") { returnType = BooleanArrayType }
        challenge("doubleAll") { returnType = IntArrayType }
        challenge("halveAll") { returnType = FloatArrayType }
        challenge("splitName") { returnType = StringArrayType }
      }
    }
  }
// --8<-- [end:array_types]

// --8<-- [start:list_types]
val listTypeExamples =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      group("List-Types") {
        packageName = "lists"

        challenge("allPositive") { returnType = BooleanListType }
        challenge("doubled") { returnType = IntListType }
        challenge("averages") { returnType = FloatListType }
        challenge("words") { returnType = StringListType }
      }
    }
  }
// --8<-- [end:list_types]
