package io.ymusic.ui

import io.ymusic.remoteconfig.RemoteConfigData

/** The app's links and contact address: the remote config's value when it sends one, else the built-in one. */
internal class AppLinks(config: RemoteConfigData?) {
    val website: String = config?.features?.websiteUrl ?: "https://github.com/pritam-79/PRVN-Music"
    val telegram: String = config?.features?.telegramUrl ?: ""
    val feedbackEmail: String = config?.features?.feedbackEmail ?: "support@prvn.music"
    val feedbackIssue: String = config?.features?.feedbackIssueUrl ?: "https://github.com/pritam-79/PRVN-Music/issues/new"
    val sourceCode: String = config?.features?.sourceCodeUrl ?: "https://github.com/pritam-79/PRVN-Music"
    val terms: String = config?.features?.termsUrl ?: "https://github.com/pritam-79/PRVN-Music/blob/main/TERMS.md"
    val changelog: String = config?.features?.changelogUrl ?: "https://github.com/pritam-79/PRVN-Music/blob/main/CHANGELOG.md"
}
