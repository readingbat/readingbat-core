@file:Suppress("unused")

package website

import com.readingbat.common.EnvVar
import com.readingbat.common.Property

// --8<-- [start:reading_properties]
fun propertyExamples() {
  // Read a property with a default value
  val isProduction = Property.IS_PRODUCTION.getProperty(false)
  val dbmsUrl = Property.DBMS_URL.getProperty("jdbc:pgsql://localhost:5432/readingbat")

  // Read a string property or null if not set
  val analyticsId = Property.ANALYTICS_ID.getPropertyOrNull()

  // Read a required property (throws if missing)
  val apiKey = Property.RESEND_API_KEY.getRequiredProperty()

  // Check if a property has been defined
  val isDefined = Property.DBMS_ENABLED.isADefinedProperty()
}
// --8<-- [end:reading_properties]

// --8<-- [start:env_var_usage]
fun envVarExamples() {
  // Check if an environment variable is defined
  val hasDbUrl = EnvVar.DBMS_URL.isDefined()

  // Get an environment variable with a fallback default
  val dbUrl = EnvVar.DBMS_URL.getEnv("jdbc:pgsql://localhost:5432/readingbat")

  // Get an environment variable or null
  val maybeKey = EnvVar.IPGEOLOCATION_KEY.getEnvOrNull()

  // Get a required environment variable (throws if missing)
  val requiredPassword = EnvVar.DBMS_PASSWORD.getRequiredEnv()

  // Boolean environment variable with default
  val agentEnabled = EnvVar.AGENT_ENABLED.getEnv(false)
}
// --8<-- [end:env_var_usage]

// --8<-- [start:env_overrides_property]
fun envOverridesPropertyExample() {
  // Typical pattern: environment variable overrides HOCON config value
  // EnvVar takes precedence when defined; otherwise falls back to Property
  val dbmsUrl =
    EnvVar.DBMS_URL.getEnv(
      Property.DBMS_URL.getProperty("jdbc:pgsql://localhost:5432/readingbat"),
    )

  val oauthClientId =
    EnvVar.GITHUB_OAUTH_CLIENT_ID.getEnv(
      Property.GITHUB_OAUTH_CLIENT_ID.getProperty(""),
    )
}
// --8<-- [end:env_overrides_property]
