import TestData.readTestContent
import com.github.pambrose.common.email.Email
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.github.readingbat.common.User
import com.github.readingbat.dsl.DataException
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.FullName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.testing.testApplication

class EnrollmentWorkflowTest : StringSpec() {
  init {
    "enrollInClass should update student enrolledClassCode" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher EnrollWF"),
                emailVal = Email("teacher-enrollwf@test.com"),
                provider = "github",
                providerId = "teacher-enrollwf-001",
                accessToken = "token-teacher-enrollwf",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Enrollment Workflow Class")

            val student =
              User.createOAuthUser(
                name = FullName("Student EnrollWF"),
                emailVal = Email("student-enrollwf@test.com"),
                provider = "github",
                providerId = "student-enrollwf-001",
                accessToken = "token-student-enrollwf",
              )

            student.enrolledClassCode shouldBe DISABLED_CLASS_CODE

            student.enrollInClass(classCode)

            student.enrolledClassCode shouldBe classCode
            student.isEnrolled(classCode) shouldBe true
          }
        }
    }

    "withdrawFromClass should remove enrollment" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Withdraw"),
                emailVal = Email("teacher-withdraw@test.com"),
                provider = "github",
                providerId = "teacher-withdraw-001",
                accessToken = "token-teacher-withdraw",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Withdraw Test Class")

            val student =
              User.createOAuthUser(
                name = FullName("Student Withdraw"),
                emailVal = Email("student-withdraw@test.com"),
                provider = "github",
                providerId = "student-withdraw-001",
                accessToken = "token-student-withdraw",
              )

            student.enrollInClass(classCode)
            student.isEnrolled(classCode) shouldBe true

            student.withdrawFromClass(classCode)

            student.isEnrolled(classCode) shouldBe false
            student.enrolledClassCode shouldBe DISABLED_CLASS_CODE
          }
        }
    }

    "enrollInClass with invalid class code should throw DataException" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val student =
              User.createOAuthUser(
                name = FullName("Student Invalid"),
                emailVal = Email("student-invalid@test.com"),
                provider = "github",
                providerId = "student-invalid-001",
                accessToken = "token-student-invalid",
              )

            val exception =
              shouldThrow<DataException> {
                student.enrollInClass(ClassCode("nonexistent-code"))
              }

            exception.message shouldContain "Invalid class code"
          }
        }
    }

    "enrollInClass when already enrolled should throw DataException" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher DupEnroll"),
                emailVal = Email("teacher-dupenroll@test.com"),
                provider = "github",
                providerId = "teacher-dupenroll-001",
                accessToken = "token-teacher-dupenroll",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Duplicate Enrollment Class")

            val student =
              User.createOAuthUser(
                name = FullName("Student DupEnroll"),
                emailVal = Email("student-dupenroll@test.com"),
                provider = "github",
                providerId = "student-dupenroll-001",
                accessToken = "token-student-dupenroll",
              )

            student.enrollInClass(classCode)

            val exception =
              shouldThrow<DataException> {
                student.enrollInClass(classCode)
              }

            exception.message shouldContain "Already enrolled"
          }
        }
    }

    "enrolling in a new class should remove from previous class" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Switch"),
                emailVal = Email("teacher-switch@test.com"),
                provider = "github",
                providerId = "teacher-switch-001",
                accessToken = "token-teacher-switch",
              )

            val classA = ClassCode.newClassCode()
            val classB = ClassCode.newClassCode()
            teacher.addClassCode(classA, "Class A Switch")
            teacher.addClassCode(classB, "Class B Switch")

            val student =
              User.createOAuthUser(
                name = FullName("Student Switch"),
                emailVal = Email("student-switch@test.com"),
                provider = "github",
                providerId = "student-switch-001",
                accessToken = "token-student-switch",
              )

            student.enrollInClass(classA)
            student.isEnrolled(classA) shouldBe true

            // Withdraw from A, then enroll in B
            student.withdrawFromClass(classA)
            student.enrollInClass(classB)

            student.isEnrolled(classA) shouldBe false
            student.isEnrolled(classB) shouldBe true
            student.enrolledClassCode shouldBe classB
          }
        }
    }

    "withdrawFromClass with disabled class code should throw DataException" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val student =
              User.createOAuthUser(
                name = FullName("Student NotEnrolled"),
                emailVal = Email("student-notenrolled@test.com"),
                provider = "github",
                providerId = "student-notenrolled-001",
                accessToken = "token-student-notenrolled",
              )

            shouldThrow<DataException> {
              student.withdrawFromClass(DISABLED_CLASS_CODE)
            }
          }
        }
    }

    "fetchEnrollees count should decrease after withdrawal" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher EnrolleeCount"),
                emailVal = Email("teacher-enrolleecount@test.com"),
                provider = "github",
                providerId = "teacher-enrolleecount-001",
                accessToken = "token-teacher-enrolleecount",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Enrollee Count Class")

            val student =
              User.createOAuthUser(
                name = FullName("Student EnrolleeCount"),
                emailVal = Email("student-enrolleecount@test.com"),
                provider = "github",
                providerId = "student-enrolleecount-001",
                accessToken = "token-student-enrolleecount",
              )

            student.enrollInClass(classCode)
            classCode.fetchEnrollees() shouldHaveSize 1

            student.withdrawFromClass(classCode)
            classCode.fetchEnrollees() shouldHaveSize 0
          }
        }
    }
  }
}
