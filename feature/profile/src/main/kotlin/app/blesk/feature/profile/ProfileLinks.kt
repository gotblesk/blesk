package app.blesk.feature.profile

import app.blesk.shared.protocol.ProfileLink

/** Your profile link; [key] lets it find you even when you hide from search. */
fun profileLink(username: String, key: String? = null) = ProfileLink.format(username, key)
