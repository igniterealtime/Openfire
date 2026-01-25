# TLS-based Channel Binding Plan

This document outlines the plan for enabling TLS-based Channel Binding in Openfire.

## Objective
Enable support for SASL channel binding (RFC 5056). This implementation will support the following channel binding types:
*   `tls-server-endpoint` (RFC 5929): Supported for all TLS versions. This is the preferred type for XMPP.
*   `tls-unique` (RFC 5929): Supported for TLS 1.2 and below. Note that this type is not defined for TLS 1.3.
*   `tls-exporter` (RFC 9266): Supported for TLS 1.3. This uses the TLS exporter interface to derive channel binding data.

The primary focus will be on `tls-server-endpoint` for SCRAM-SHA-1-PLUS and other `-PLUS` mechanisms, as per XEP-0440.

## Current State Analysis
*   **SASL Implementation**: Openfire uses a custom SASL provider (`SaslProvider`) and factory (`SaslServerFactoryImpl`) registered in `SASLAuthentication`.
*   **SASL Mechanisms**: Currently supports PLAIN, ANONYMOUS, SCRAM-SHA-1, EXTERNAL, and JIVE-SHAREDSECRET.
*   **TLS Implementation**: Managed via Netty (`NettyConnection`) using `SslHandler`.
*   **Certificate Access**: `NettyConnection` can already provide local and peer certificates.
*   **Bouncy Castle**: Already a dependency and used for various cryptographic tasks, but not as the primary JSSE provider for XMPP connections.

## Proposed Changes

### 1. TLS Provider Replacement
To ensure consistent support for channel binding and better control over TLS, we may need to explicitly use Bouncy Castle as the TLS provider.
*   Update `EncryptionArtifactFactory` to prefer Bouncy Castle Provider for `SSLContext`.
*   Ensure `BouncyCastleProvider` is registered early in the server lifecycle.

### 2. Connection Interface Enhancement
*   Add a method to `org.jivesoftware.openfire.Connection` to retrieve channel binding data for a specific type (e.g., `byte[] getChannelBindingData(String type)`).
*   Implement this in `NettyConnection`.
    *   For `tls-server-endpoint`, this involves:
        *   Retrieving the server's leaf certificate.
        *   Calculating the hash using the certificate's signature algorithm (as per RFC 5929).
    *   For `tls-unique`, this involves:
        *   Retrieving the TLS Finished messages from the `SSLSession` (only available for TLS 1.2 and below).
    *   For `tls-exporter`, this involves:
        *   Using the TLS Exporter interface (RFC 5705) as implemented in TLS 1.3 to derive the binding data using the label "EXPORTER-Channel-Binding" and no context.

### 3. SASL Mechanism Updates
*   Implement `SCRAM-SHA-1-PLUS` in `SaslServerFactoryImpl`.
*   The `ScramSha1SaslServer` (or a new `ScramPlusSaslServer` base) needs to:
    *   Accept channel binding data from the `Connection` (available via `props` in `createSaslServer`).
    *   Verify the `gs2-cb-flag` and `gs2-header` according to RFC 5802.
    *   Incorporate channel binding data into the SCRAM signature verification.

### 4. Configuration
*   Add a system property (e.g., `sasl.channel-binding.enabled`) to toggle this feature.
*   Update `SASLAuthentication` to advertise `-PLUS` mechanisms only when TLS is active and channel binding is enabled.

## Implementation Steps

1.  **Phase 1: Research & Prototype** [DONE]
    *   Verify Bouncy Castle JSSE integration.
        *   Determined that `tls-server-endpoint` can be implemented using standard JSSE by hashing the server certificate.
        *   `tls-unique` and `tls-exporter` require access to internal TLS data (Finished messages, Keying Material Export) not exposed by standard Java `SSLSession` API.
        *   Bouncy Castle JSSE (`bctls`) is required to support `tls-unique` and `tls-exporter`.
    *   Prototype `tls-server-endpoint`, `tls-unique` and `tls-exporter` data extraction from `SSLSession`.
        *   `tls-server-endpoint` prototype verified in `ChannelBindingTest.java`.
2.  **Phase 2: Core Changes**
    *   Modify `Connection` and `NettyConnection`.
    *   Update `EncryptionArtifactFactory` if TLS provider change is confirmed necessary.
3.  **Phase 3: SASL Implementation**
    *   Refactor SCRAM implementation to be reusable for `-PLUS` variants.
    *   Implement `SCRAM-SHA-1-PLUS`.
4.  **Phase 4: Integration & Testing**
    *   Add unit tests for channel binding data calculation.
    *   Integration tests with a client supporting channel binding (e.g., a modified Smack or a specialized test script).

## References
*   [RFC 5056: On the Use of Channel Bindings to Secure Channels](https://tools.ietf.org/html/rfc5056)
*   [RFC 5929: Channel Bindings for TLS](https://tools.ietf.org/html/rfc5929)
*   [RFC 5802: SCRAM SASL Mechanisms](https://tools.ietf.org/html/rfc5802)
*   [RFC 9266: Channel Bindings for TLS 1.3](https://tools.ietf.org/html/rfc9266)
*   [XEP-0440: SASL Channel Binding Advertisement](https://xmpp.org/extensions/xep-0440.html)
