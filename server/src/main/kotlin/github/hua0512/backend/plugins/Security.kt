package github.hua0512.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import github.hua0512.repo.LocalDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*


const val jwtAudience = "stream-rec"
const val jwtDomain = "https://github.com/hua0512/stream-rec/"
internal val jwtSecret by lazy { LocalDataSource.getJwtSecret() }
const val jwtRealm = "stream-rec-jwt-realm"

fun Application.configureSecurity() {
  authentication {
    jwt("auth-jwt") {
      realm = jwtRealm
      verifier(
        JWT
          .require(Algorithm.HMAC256(jwtSecret))
          .withAudience(jwtAudience)
          .withIssuer(jwtDomain)
          .build()
      )
      validate { credential ->
        if (credential.payload.audience.contains(jwtAudience)) {
          // check date
          if (credential.payload.expiresAt.time < kotlin.time.Clock.System.now().toEpochMilliseconds()) {
            return@validate null
          }
          // check user
          val username = credential.payload.getClaim("username").asString() ?: return@validate null
          JWTPrincipal(credential.payload)
        } else null
      }
      challenge { defaultScheme, realm ->
        call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
      }
    }
  }
}
