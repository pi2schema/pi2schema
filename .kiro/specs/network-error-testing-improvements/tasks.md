# Implementation Plan

- [x] 1. Add WireMock dependency and basic setup
  - Add WireMock test dependency to build.gradle
  - Replace external URL dependencies with local WireMock server in existing tests
  - _Requirements: 2.1, 2.2, 6.1_

- [x] 2. Replace log assertions with exception verification
  - Remove ListAppender setup and log message assertions
  - Replace log content checks with direct exception type and message verification
  - Verify VaultConnectivityException is thrown with appropriate cause exceptions
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 3. Implement controlled network condition simulation
  - Replace external URLs (httpbin.org, non-routable IPs) with WireMock stubs
  - Create simple helper methods for common network failures (timeout, refused, DNS)
  - Use WireMock's delay and error response features for deterministic testing
  - _Requirements: 2.1, 2.3, 2.4, 6.2_

- [ ] 4. Add retry behavior verification through request counting
  - Use WireMock request verification to count actual retry attempts
  - Remove log-based retry verification and replace with request count assertions
  - Verify non-retryable exceptions don't trigger retries
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 5. Enhance exception content validation
  - Verify exception messages contain useful information (sanitized URLs, timeouts)
  - Ensure sensitive information (tokens) is not exposed in exception messages
  - Test that original cause exceptions are properly chained
  - _Requirements: 3.1, 3.2, 3.3, 7.5_

- [ ] 6. Optimize test execution time and reliability
  - Replace long timeouts with short, controlled delays
  - Remove dependency on external network connectivity
  - Ensure tests complete within reasonable time limits
  - _Requirements: 6.1, 6.2, 6.3, 6.4_