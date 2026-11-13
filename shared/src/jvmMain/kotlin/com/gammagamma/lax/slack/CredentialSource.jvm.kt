package com.gammagamma.lax.slack

import com.gammagamma.lax.slack.desktop.DesktopCredentialSource

actual fun createCredentialSource(): CredentialSource = DesktopCredentialSource()
