package com.invernadero.nube

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** La persona cerró la hoja de Google sin elegir cuenta: no es un error que haya que mostrar. */
class GoogleCancelado : Exception("Cancelado")

/**
 * "Continuar con Google" con Credential Manager (la API que Android recomienda para iniciar sesión).
 * Devuelve el ID token de Google, que luego se cambia por una sesión de Firebase en [Nube.entrarConGoogle].
 * [contexto] debe ser la Activity (la hoja de Google se abre sobre ella).
 *
 * El id de cliente web ("default_web_client_id") lo genera el plugin google-services a partir de
 * google-services.json, solo si Google está activado en Firebase Authentication. Se busca por nombre para
 * que la app compile aunque todavía no lo esté.
 */
@SuppressLint("DiscouragedApi")
suspend fun tokenDeGoogle(contexto: Context): String {
    val id = contexto.resources.getIdentifier("default_web_client_id", "string", contexto.packageName)
    if (id == 0) throw ErrorNube(
        "Para entrar con Google, activa «Google» en Firebase Authentication, agrega la huella SHA-1 y descarga de nuevo google-services.json."
    )
    val opcion = GetSignInWithGoogleOption.Builder(contexto.getString(id)).build()
    val pedido = GetCredentialRequest.Builder().addCredentialOption(opcion).build()
    val respuesta = try {
        CredentialManager.create(contexto).getCredential(contexto, pedido)
    } catch (e: GetCredentialCancellationException) {
        throw GoogleCancelado()
    } catch (e: NoCredentialException) {
        throw ErrorNube("No hay cuentas de Google en este teléfono. Agrega una en los ajustes del teléfono.")
    } catch (e: GetCredentialException) {
        throw ErrorNube("Google no respondió. Revisa tu conexión y que la huella SHA-1 de la app esté en Firebase.")
    }
    val credencial = respuesta.credential
    if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return try {
            GoogleIdTokenCredential.createFrom(credencial.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw ErrorNube("Google devolvió una respuesta no válida")
        }
    }
    throw ErrorNube("Google devolvió una credencial que GreenBox no reconoce")
}
