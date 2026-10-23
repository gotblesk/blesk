package app.blesk.server.keys

import app.blesk.server.Services
import app.blesk.server.auth.Signatures
import app.blesk.server.auth.account
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.decodeBase64
import app.blesk.server.http.encodeBase64
import app.blesk.server.http.ensure
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.OneTimeKey
import app.blesk.shared.protocol.PreKeyBundleDto
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SignedKey
import app.blesk.shared.protocol.Usernames
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey

/** Most one-time keys of each kind the server keeps per account. */
const val ONE_TIME_KEY_CAP = 200

/** Below this many one-time keys of either kind the client is asked to upload more. */
const val KEYS_LOW_THRESHOLD = 20

fun KeyStatus.isLow() = preKeys < KEYS_LOW_THRESHOLD || kyberPreKeys < KEYS_LOW_THRESHOLD

fun Route.keyRoutes(services: Services) {
    put(Api.KEYS) {
        val upload = call.receiveJson<KeyUpload>()
        val account = services.accounts.find(call.account.username).orFail { notFound() }
        val identityKey = Signatures.identityKey(account.identityKey).orFail { badRequest(ErrorCodes.INVALID_KEY) }
        val validated = validate(upload, identityKey)
        ensure(services.keys.upload(account.id, validated, ONE_TIME_KEY_CAP)) {
            badRequest(ErrorCodes.TOO_MANY_KEYS, "at most $ONE_TIME_KEY_CAP one-time keys of each kind")
        }
        call.respond(services.keys.status(account.id))
    }

    get(Api.KEY_STATUS) {
        call.respond(services.keys.status(call.account.id))
    }

    get("${Api.KEYS}/{username}") {
        val username = call.parameters["username"].orEmpty()
        ensure(Usernames.isWellFormed(username)) { notFound() }
        val owner = services.accounts.find(username).orFail { notFound() }
        // Without this a hidden account could still be found, and written to, through its keys.
        ensure(owner.visibleTo(call.account.id, call.request.queryParameters[Api.LINK])) { notFound() }
        val oneTimeKeys = services.oneTimeKeyBudget.tryAcquire("${call.account.id}:${owner.id}")
        val bundle = services.keys.takeBundle(owner.id, oneTimeKeys).orFail { notFound(ErrorCodes.NO_KEYS) }
        if (bundle.remaining.isLow()) {
            services.hub.push(owner.id, ServerFrame.KeysLow(bundle.remaining.preKeys, bundle.remaining.kyberPreKeys))
        }
        call.respond(
            PreKeyBundleDto(
                username = owner.username,
                registrationId = owner.registrationId,
                deviceId = Api.DEVICE_ID,
                identityKey = owner.identityKey.encodeBase64(),
                signedPreKey = bundle.signedPreKey.toSignedKey(),
                preKey = bundle.preKey?.let { OneTimeKey(it.id, it.publicKey.encodeBase64()) },
                kyberPreKey = bundle.kyberPreKey.toSignedKey(),
            ),
        )
    }
}

private fun validate(upload: KeyUpload, identityKey: IdentityKey): ValidatedUpload {
    if (upload.preKeys.size > ONE_TIME_KEY_CAP || upload.kyberPreKeys.size > ONE_TIME_KEY_CAP) {
        throw badRequest(ErrorCodes.TOO_MANY_KEYS)
    }
    return ValidatedUpload(
        signedPreKey = upload.signedPreKey?.let { verifySigned(it, identityKey, ::ECPublicKey) },
        kyberLastResort = upload.kyberLastResort?.let { verifySigned(it, identityKey, ::KEMPublicKey) },
        preKeys = upload.preKeys.map { key ->
            val bytes = key.publicKey.decodeBase64(ErrorCodes.INVALID_KEY)
            requireParses(bytes, ::ECPublicKey)
            StoredKey(key.id, bytes, null)
        },
        kyberPreKeys = upload.kyberPreKeys.map { verifySigned(it, identityKey, ::KEMPublicKey) },
    )
}

private fun verifySigned(key: SignedKey, identityKey: IdentityKey, parse: (ByteArray) -> Any): StoredKey {
    val bytes = key.publicKey.decodeBase64(ErrorCodes.INVALID_KEY)
    val signature = key.signature.decodeBase64(ErrorCodes.INVALID_SIGNATURE)
    requireParses(bytes, parse)
    ensure(Signatures.verify(identityKey, bytes, signature)) { badRequest(ErrorCodes.INVALID_SIGNATURE) }
    return StoredKey(key.id, bytes, signature)
}

private fun requireParses(bytes: ByteArray, parse: (ByteArray) -> Any) {
    try {
        parse(bytes)
    } catch (invalid: InvalidKeyException) {
        throw badRequest(ErrorCodes.INVALID_KEY, cause = invalid)
    }
}

private fun StoredKey.toSignedKey() = SignedKey(id, publicKey.encodeBase64(), checkNotNull(signature).encodeBase64())
