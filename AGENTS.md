# AGENTS.md

Guidance for AI coding agents contributing to Openfire. It is advisory — match the surrounding code when it differs. See the README for the full build and developer instructions.

## Build & test

- Java 17, built with the Maven wrapper.
- Build the full project with `./mvnw verify`; scope to a module and its dependencies with `-pl <module> -am` (for example `-pl distribution -am`).
- Run one test class: `./mvnw -pl xmppserver test -Dtest=ClassName`.
- The runnable server lands in `distribution/target/distribution-base/`; start it with `bin/openfire.sh` (`-demoboot` for test data, `-debug` for a debugger).

## Code style

- UK English in comments and documentation (organisation, behaviour, colour).
- New files carry the current-year Apache 2.0 header (Ignite Realtime Foundation); bump the end year on existing files you change.
- Javadoc: plain text, no HTML tags; blank lines between paragraphs; `@author` on new classes.
- Prefer constants to magic strings; check `JiveGlobals` for an existing key first.
- Expose tunables as a `SystemProperty` (with a description in the i18n files), not raw `JiveGlobals.getXxxProperty(...)`.
- Match surrounding style in legacy code; use modern idioms (try-with-resources, `java.nio.file`, `java.time`) in new code; don't refactor working code for style alone.

## Tests

- JUnit 5 (`org.junit.jupiter`) and Mockito.
- Every test has at least one assertion and a name stating the behaviour (never `test1`).
- Give every assertion a message stating the behaviour it verifies (JUnit 5 puts the message last); for Mockito, use `verify(x, times(1).description("...")).method()`. Keep messages specific.
- Arrange-Act-Assert, with a comment marking each section in longer tests.
- Test Openfire's behaviour, not the JDK's or a library's.

## Internationalisation

- User-facing strings and `SystemProperty` descriptions go in `i18n/src/main/resources/openfire_i18n.properties` (English, required). Translations in the other `openfire_i18n_*.properties` files are welcome where you can provide them, but English is the only one required.

## Commits & pull requests

- One branch and one pull request per Jira ticket, named after it: branch `OF-1234`, title `OF-1234: Short description`.
- Commit subjects are prefixed with the Jira key (`OF-1234: ...`), so `git log --grep=OF-1234` finds prior work.
- Architecture Decision Records go in `doc/adr/`; see `doc/adr/README.md` and `doc/adr/000-record-architecture-decisions.md` for the process.
