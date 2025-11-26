# ADR-000: Record Architecture Decisions

## Status

Accepted

## Context

As the Openfire project evolves, architectural decisions are made by various contributors over time. Without a structured way to document these decisions, maintainers face several problems:

- New contributors lack context on why things are built the way they are
- The same debates resurface because previous reasoning isn't recorded
- It's difficult to evaluate whether past decisions still hold given changed circumstances
- Knowledge is lost when contributors move on

We need a lightweight, version-controlled way to capture the "why" behind significant architectural choices, not just what was decided, but what alternatives were considered and rejected.

## Options Considered

### Option 1: No Formal Documentation

Continue making decisions in issues, pull requests, and chat without structured records.

**Consequences**:

- ✅ Zero overhead, no process to follow
- ✅ Decisions happen naturally in existing workflows
- ❌ Context scattered across multiple systems (GitHub, Jira, chat, email)
- ❌ Hard to find historical reasoning
- ❌ Same discussions repeated as team changes
- ❌ No visibility into rejected alternatives

### Option 2: Wiki Pages

Use GitHub Wiki or similar to document decisions.

**Consequences**:

- ✅ Easy to edit and update
- ✅ Familiar format for most contributors
- ❌ Not version-controlled alongside code
- ❌ Easy to silently edit history, losing original reasoning
- ❌ Disconnected from the codebase it describes
- ❌ No review process for changes

### Option 3: Architecture Decision Records (ADRs)

Use short, structured Markdown documents stored in the repository following the ADR format.

**Consequences**:

- ✅ Version-controlled with the code
- ✅ Immutable once accepted—preserves historical context
- ✅ Reviewed via pull requests like any other change
- ✅ Lightweight format—easy to write and read
- ✅ Well-established convention with tooling support (adr-tools)
- ✅ Documents rejected alternatives, not just chosen approach
- ❌ Requires discipline to write ADRs for significant decisions
- ❌ Small overhead for each architectural decision

### Option 4: Formal RFC Process

Use a heavier Request for Comments process with detailed templates and approval workflows.

**Consequences**:

- ✅ Thorough analysis before decisions
- ✅ Clear approval process
- ❌ High overhead discourages documentation
- ❌ Overkill for many architectural decisions
- ❌ Can slow down development unnecessarily

## Decision

We will use Architecture Decision Records (ADRs), stored as Markdown files in `doc/adr/` within the repository.

Specifically:

- **Location**: `doc/adr/` (the conventional default used by adr-tools)
- **Format**: Markdown files following the structure defined in `doc/adr/README.md`
- **Naming**: `NNN-title-in-kebab-case.md` with zero-padded numbers
- **Immutability**: ADRs are never modified after acceptance; superseded decisions get a new ADR

## Rationale

ADRs strike the right balance between rigour and pragmatism. They're lightweight enough that people will actually write them, but structured enough to capture the essential context future readers need.

Storing them in `doc/adr/` rather than within the existing `documentation/` folder keeps developer-facing architectural records separate from end-user documentation. The `doc/adr/` path is also the default expected by `adr-tools`, meaning contributors can use standard tooling without configuration.

The immutability principle is particularly valuable, it prevents historical revisionism and makes it easy to spot "hot topics" where decisions keep changing, which often indicates areas needing deeper analysis.

## Consequences

- All significant architectural decisions should have an accompanying ADR
- Contributors should check existing ADRs before proposing changes to established patterns
- The `doc/adr/` directory will grow over time as a historical record
- New contributors can read ADRs to understand project conventions and their rationale
- Pull requests for architectural changes should include or reference an ADR
- Tooling like adr-tools can be used to create new ADRs with consistent formatting
