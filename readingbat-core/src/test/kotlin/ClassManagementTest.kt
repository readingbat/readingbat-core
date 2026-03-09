import TestData.readTestContent
import com.github.pambrose.common.email.Email
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.User
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.FullName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ClassManagementTest : StringSpec() {
  init {
    "addClassCode should create a class and classCount should reflect it" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher ClassMgmt"),
                emailVal = Email("teacher-classmgmt@test.com"),
                provider = "github",
                providerId = "teacher-classmgmt-001",
                accessToken = "token-teacher-classmgmt",
              )

            teacher.classCount() shouldBe 0

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Intro to Java")

            teacher.classCount() shouldBe 1
            teacher.classCodes() shouldContain classCode
          }
        }
    }

    "classCodes should list all classes created by teacher" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher MultiClass"),
                emailVal = Email("teacher-multiclass@test.com"),
                provider = "github",
                providerId = "teacher-multiclass-001",
                accessToken = "token-teacher-multiclass",
              )

            val classA = ClassCode.newClassCode()
            val classB = ClassCode.newClassCode()
            val classC = ClassCode.newClassCode()

            teacher.addClassCode(classA, "Class A")
            teacher.addClassCode(classB, "Class B")
            teacher.addClassCode(classC, "Class C")

            val codes = teacher.classCodes()
            codes shouldHaveSize 3
            codes shouldContain classA
            codes shouldContain classB
            codes shouldContain classC
          }
        }
    }

    "isValid and isNotValid should reflect class existence" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Valid"),
                emailVal = Email("teacher-valid@test.com"),
                provider = "github",
                providerId = "teacher-valid-001",
                accessToken = "token-teacher-valid",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Valid Class")

            classCode.isValid() shouldBe true
            classCode.isNotValid() shouldBe false

            ClassCode("nonexistent-class-code").isValid() shouldBe false
          }
        }
    }

    "fetchClassDesc should return the class description" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Desc"),
                emailVal = Email("teacher-desc@test.com"),
                provider = "github",
                providerId = "teacher-desc-001",
                accessToken = "token-teacher-desc",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Advanced Python")

            classCode.fetchClassDesc() shouldBe "Advanced Python"
            classCode.fetchClassDesc(quoted = true) shouldBe "\"Advanced Python\""
          }
        }
    }

    "fetchClassTeacherId should return the teacher userId" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher TeacherId"),
                emailVal = Email("teacher-teacherid@test.com"),
                provider = "github",
                providerId = "teacher-teacherid-001",
                accessToken = "token-teacher-teacherid",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "TeacherId Test Class")

            classCode.fetchClassTeacherId() shouldBe teacher.userId
          }
        }
    }

    "fetchEnrollees should list enrolled students" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Enrollees"),
                emailVal = Email("teacher-enrollees@test.com"),
                provider = "github",
                providerId = "teacher-enrollees-001",
                accessToken = "token-teacher-enrollees",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Enrollees Test")

            classCode.fetchEnrollees().shouldBeEmpty()

            val student1 =
              User.createOAuthUser(
                name = FullName("Student One"),
                emailVal = Email("student-one-enrollees@test.com"),
                provider = "github",
                providerId = "student-one-enrollees-001",
                accessToken = "token-student-one-enrollees",
              )
            val student2 =
              User.createOAuthUser(
                name = FullName("Student Two"),
                emailVal = Email("student-two-enrollees@test.com"),
                provider = "github",
                providerId = "student-two-enrollees-001",
                accessToken = "token-student-two-enrollees",
              )

            student1.enrollInClass(classCode)
            student2.enrollInClass(classCode)

            val enrollees = classCode.fetchEnrollees()
            enrollees shouldHaveSize 2
          }
        }
    }

    "isUniqueClassDesc should detect duplicate descriptions" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Unique"),
                emailVal = Email("teacher-unique@test.com"),
                provider = "github",
                providerId = "teacher-unique-001",
                accessToken = "token-teacher-unique",
              )

            teacher.isUniqueClassDesc("Unique Class Name") shouldBe true

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Unique Class Name")

            teacher.isUniqueClassDesc("Unique Class Name") shouldBe false
            teacher.isUniqueClassDesc("Different Class Name") shouldBe true
          }
        }
    }

    "deleteClassCode should remove the class" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher DeleteClass"),
                emailVal = Email("teacher-deleteclass@test.com"),
                provider = "github",
                providerId = "teacher-deleteclass-001",
                accessToken = "token-teacher-deleteclass",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Doomed Class")

            classCode.isValid() shouldBe true

            transaction {
              classCode.deleteClassCode()
            }

            classCode.isValid() shouldBe false
          }
        }
    }

    "DISABLED_CLASS_CODE fetchEnrollees should return empty list" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            DISABLED_CLASS_CODE.fetchEnrollees().shouldBeEmpty()
          }
        }
    }
  }
}
