# ADR-010: Compatibility of Public Methods for Plugins

## Status

Accepted

## Context

Openfire is extended by plugins, many of them written and maintained by third parties. A plugin is compiled against one release of Openfire, and is often installed on later releases without being rebuilt. It can call any public or protected member of Openfire's classes: Openfire has no separate API module, and nothing marks which classes are meant to be used by plugins. Plugins use classes such as `SessionManager`, `RoutingTable`, the session classes and the user, group and roster managers, but which of their members a third-party plugin calls cannot be checked.

When a public or protected member is renamed, moved to another class or given another signature, a plugin that calls it is no longer binary compatible with Openfire. The Java runtime reports this only when the call is first made, with a `NoSuchMethodError` or `NoSuchFieldError`, which can be long after the plugin was loaded. Administrators then find out that a plugin is broken when a feature fails in production, and plugin developers find out from their users.

Refactoring needs to rename and move members all the same: names that no longer describe what a member does, or classes that hold unrelated concerns, make the code harder to change. Engineering plan OF-2425 (Stream Management for server-to-server connections) made such a choice for the classes that it changed, as its decision 8. This ADR applies that decision to all of Openfire.

## Options Considered

### Option 1: Change Public Members Freely, and List the Changes in the Changelog

**Description**: Rename, move and change members as refactoring requires. List the changes in the changelog, so that plugin developers can adapt.

**Consequences**:

- ✅ No extra code, and refactoring is as easy as for internal code
- ❌ Plugins break without warning, at the first call of a changed member, possibly in production
- ❌ Plugin developers must read every changelog, and cannot build one version of their plugin that works with two consecutive releases of Openfire
- ❌ Third-party plugins that are no longer maintained stop working

### Option 2: Keep Old Signatures Permanently

**Description**: Never remove a public or protected member. A renamed or moved member keeps its old signature as a delegate, indefinitely.

**Consequences**:

- ✅ No plugin ever breaks over a renamed or moved member
- ❌ The API keeps growing, with old names that no longer describe what the code does
- ❌ Readers have to work out which of two members to use, and the old ones keep being called by new code

### Option 3: Define a Public API, and Only Keep That Compatible

**Description**: Mark the classes and members that plugins may use, for example with an annotation or a separate module, and keep only those compatible. Everything else can change freely.

**Consequences**:

- ✅ Clear for plugin developers and for contributors which members are safe to use and which are safe to change
- ❌ A large effort: the members that plugins use today would first have to be identified, including those of third-party plugins, which cannot be checked
- ❌ Until it is complete, it does not answer what to do with the members that are not marked

### Option 4: Deprecated Delegates for One Minor Release (CHOSEN)

**Description**: A public or protected member that is renamed, moved or given another signature keeps its old signature, as a deprecated delegate to the new one, until the next minor release.

**Consequences**:

- ✅ A plugin that calls a changed member keeps working, binary compatible, until the removal release
- ✅ Plugin developers get a compiler warning that names the replacement, and have a release to adapt
- ✅ Delegates are cheap to add and to remove, and the removal release is written next to each of them
- ❌ Old and new members exist side by side for a release
- ❌ Does not help where a member cannot keep its meaning, for example because its behaviour changes

## Decision

We will implement **Option 4: Deprecated delegates for one minor release**.

Specifically:

- **What it applies to**: public and protected members (methods, constructors, fields and nested classes) of classes in Openfire's own modules that are renamed, moved to another class, or given another signature. That includes members that are only meant to be called as part of Openfire's own processing, as a plugin may call them all the same. The Javadoc of such a delegate says that it is meant for Openfire's own processing.
- **Delegate**: the old signature stays, and delegates to the new one. It is marked `@Deprecated(forRemoval = true, since = "<release>")`, followed by a `// Remove in or after Openfire <release>` comment, as elsewhere in Openfire. `since` is the release that ships the change, and removal is in or after the next minor release. The `@deprecated` tag in the Javadoc of the delegate names its replacement.
- **No delegate where the meaning changes**: where the old signature cannot keep its meaning, for example because the behaviour of the member changes, or because an abstract class cannot keep a public constructor, there is no delegate. The change is listed as an API change in the pull request and in the changelog.
- **Narrower visibility**: making a member less visible (for example public to protected) is an API change, and is listed as such.

## Rationale

A delegate keeps plugins working at the cost of a few lines, and the deprecation tells their developers what to change, while they can still build one version of their plugin for two consecutive releases. Removing the delegate in the next minor release keeps the old names from lingering, which option 2 would not.

A delegate that behaves differently from the old member would hide a change that its callers need to know about. Where the meaning changes, the compiler or the runtime reporting the change is better than a member that silently does something else, which is why there is no delegate in that case.

Option 3 would be clearer in the long run, but needs an inventory that cannot be made for third-party plugins. This decision does not stand in its way: once a public API is defined, this ADR can be limited to it.

## Consequences

### What Becomes Easier

- Plugins keep working on a release that renames or moves members they call
- Refactoring is not held back by fear of breaking plugins, as the cost of a change is known: a delegate, and its removal later

### What Becomes Harder

- Each pull request that changes a public or protected member must add delegates, and review must check for them
- Delegates have to be removed in the release that their comment names

### Constraints

- A change that cannot keep a delegate must be listed as an API change in the pull request and in the changelog
- Compatibility of Openfire with older versions of itself, in what it sends, stores and does, is the subject of ADR-011

## References

- **OF-2425**: Stream Management for server-to-server connections (engineering plan: `doc/plan/2026-10-23-stream-management-s2s.md`, decision 8)
- **ADR-011**: Backwards Compatibility With Older Versions of Openfire
