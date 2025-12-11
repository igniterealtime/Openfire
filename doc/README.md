# Developer Documentation

This directory contains code-level developer documentation focused on the internal architecture and design of Openfire.

## Not to be confused with `documentation/`

The [`documentation/`](../documentation/) folder at the repository root contains user-facing documentation:
- Installation and upgrade guides
- Configuration guides (SSL, LDAP, database)
- Plugin development tutorials
- End-user and administrator documentation

**This `doc/` directory** is different - it documents the internal architecture and design decisions for developers working on the Openfire codebase itself.

## Contents

### `adr/` - Architecture Decision Records

Documents significant architectural decisions made during Openfire development. ADRs capture:
- The context and problem being addressed
- Options that were considered
- The decision made and rationale
- Consequences of the decision

See [`adr/README.md`](adr/README.md) for the full ADR format and guidelines.

## When to add documentation here

Add documentation to this directory when:
- Documenting architectural decisions (use an ADR)
- Explaining internal design patterns or conventions
- Recording technical rationale that helps future developers understand the codebase

Add documentation to `documentation/` when:
- Creating guides for users, administrators, or plugin developers
- Documenting features from an external perspective
- Writing installation, configuration, or operational guides
