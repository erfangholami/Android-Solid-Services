package com.erfangholami.androidsolidservices.api.auth.implementation

internal object OidcConstants {
    internal const val DPOP_SIGNING_ALG_VALUES_SUPPORTED : String = "dpop_signing_alg_values_supported"
    internal const val REGISTRATION_REQUEST_ID_TOKEN_SIGNED_RESPONSE_ALG : String = "id_token_signed_response_alg"
    internal const val ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED : String = "id_token_signing_alg_values_supported"
    internal const val TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED : String = "token_endpoint_auth_methods_supported"
    internal const val TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC : String = "client_secret_basic"
    internal const val TOKEN_ENDPOINT_AUTH_METHOD_NONE : String = "none"
    internal const val REGISTRATION_REQUEST_CLIENT_NAME : String = "client_name"
    internal const val REGISTRATION_REQUEST_SUBJECT_TYPE_PUBLIC : String = "public"
    internal const val REGISTRATION_REQUEST_GRANT_TYPE_AUTHORIZATION_CODE : String = "authorization_code"
    internal const val REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN : String = "refresh_token"

    internal const val AUTHORIZATION_REQUEST_PROMPT_CONSENT : String = "consent"
    internal const val AUTHORIZATION_REQUEST_PROMPT_LOGIN : String = "login"
    internal const val AUTHORIZATION_REQUEST_SCOPE_WEBID : String = "webid"
    internal const val AUTHORIZATION_REQUEST_SCOPE_OPENID : String = "openid"
    internal const val AUTHORIZATION_REQUEST_SCOPE_OFFLINE_ACCESS : String = "offline_access"

    internal const val CLIENT_AUTHENTICATION_CLIENT_ID : String = "client_id"
}
