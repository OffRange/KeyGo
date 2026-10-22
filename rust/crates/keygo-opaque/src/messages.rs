use zeroize::Zeroizing;

opaque_newtype! {
    /// Registration, client -> server
    RegistrationRequest(RegistrationRequest) as Vec<u8>;

    /// Registration, server -> client
    RegistrationResponse(RegistrationResponse) as Vec<u8>;

    /// Registration, client -> server
    RegistrationUpload(RegistrationUpload) as Vec<u8>;

    /// Login (KE1), client -> server
    CredentialRequest(CredentialRequest) as Vec<u8>;

    /// Login (KE2), server -> client
    CredentialResponse(CredentialResponse) as Vec<u8>;

    /// Login (KE3), client -> server
    CredentialFinalization(CredentialFinalization) as Vec<u8>;

    /// Per-account server record
    PasswordFile(ServerRegistration) as Zeroizing<Vec<u8>>;
}

wire_message!(
    RegistrationRequest,
    RegistrationResponse,
    RegistrationUpload,
    CredentialRequest,
    CredentialResponse,
    CredentialFinalization,
);
