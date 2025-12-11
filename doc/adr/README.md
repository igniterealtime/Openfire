# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records documenting significant architectural and design decisions made for the Openfire project.

## Purpose

ADRs document the **why** behind architectural decisions:
- What problem needed solving
- What options were considered (ALL options, not just the chosen one)
- Consequences of each option (including those not selected)
- Which option was chosen and why
- Current status of the decision

## When to Create an ADR

Create an ADR when making decisions about:
- Architectural approaches or patterns
- Technology selection (databases, frameworks, libraries)
- Design patterns or conventions
- Significant changes to existing architecture
- Trade-offs between competing approaches

**Examples**:
- Choosing a database for production
- Adopting a new XMPP extension (XEP)
- Selecting a clustering approach
- Changing authentication mechanisms

## Naming Convention

Files should follow the pattern: `NNN-title-in-kebab-case.md`

**Examples**:
- `001-use-postgresql-for-production.md`
- `002-adopt-xep-0288-bidirectional-s2s.md`
- `003-implement-plugin-hot-reload.md`

Numbers should be zero-padded to three digits (001, 002, etc.).

## Structure

Every ADR must include:

### 1. Title
Format: `# ADR-NNN: Decision Title`

### 2. Status
One of:
- **Proposed**: Under consideration
- **Accepted**: Decision made and approved
- **Superseded**: No longer current, replaced by another ADR (link to new ADR)
- **Deprecated**: No longer recommended but not formally replaced
- **Rejected**: Proposed but not accepted

### 3. Context
What is the issue or situation requiring a decision? What forces are at play?

### 4. Options Considered
**Critical**: Document ALL options evaluated, not just the chosen one.

For each option, include:
- Description of the approach
- **Consequences** (both positive ✅ and negative ❌)
- Trade-offs
- Why it was or wasn't chosen

### 5. Decision
What option was selected? State clearly and concisely.

### 6. Rationale
Why was this option chosen? What made it better than alternatives?

### 7. Consequences
Overall impact of the decision (separate from per-option consequences above):
- What becomes easier or harder?
- What new capabilities or constraints?
- What follow-up work is needed?

## Example ADR Structure

```markdown
# ADR-001: Choose Database for Production Deployment

## Status
Accepted

## Context
We need to select a production database. Currently using embedded HSQLDB
for development. Production deployments require scalability, reliability,
and operational maturity.

## Options Considered

### Option 1: PostgreSQL
**Consequences**:
- ✅ Industry standard with excellent reputation
- ✅ Strong ACID compliance and data integrity
- ✅ Excellent tooling and monitoring ecosystem
- ✅ Active community and commercial support available
- ❌ Requires separate infrastructure and management
- ❌ Additional operational complexity vs embedded
- ❌ Network latency for database calls

### Option 2: MySQL
**Consequences**:
- ✅ Widely deployed, proven at scale
- ✅ Good tooling and replication support
- ✅ Familiar to many teams
- ❌ Historical issues with ACID guarantees
- ❌ GPL licensing may be concern for some deployments
- ❌ Less robust handling of concurrent connections

### Option 3: Keep Embedded HSQLDB
**Consequences**:
- ✅ Zero configuration, bundled with Openfire
- ✅ No separate infrastructure needed
- ✅ Simplest deployment model
- ❌ Not designed for production scale
- ❌ Limited concurrency support
- ❌ No high availability or replication
- ❌ Not recommended by vendor for production

## Decision
We will use PostgreSQL for production deployments.

## Rationale
PostgreSQL provides the best balance of reliability, performance, and
ecosystem support for production XMPP workloads. While it adds
operational complexity, this is justified by significantly better
scalability and reliability characteristics. The strong ACID guarantees
are important for message integrity.

## Consequences
- Production deployments will require PostgreSQL infrastructure
- Need to maintain database migration scripts for PostgreSQL
- Documentation must cover PostgreSQL setup
- Development can continue using HSQLDB (demoboot mode)
- Need to test against PostgreSQL regularly in CI
```

## Important: ADRs are Immutable

**Once an ADR is merged to the main branch, it is NEVER modified or rewritten.**

### Why Immutable?
- Preserves historical context
- Shows evolution of thinking over time
- Allows you to spot "hot topics" that keep changing
- Prevents rewriting history to make past decisions look better

### Changing a Decision

If an architectural decision changes:

1. **Do NOT edit the original ADR**
2. Update the original ADR's status to `Superseded by ADR-NNN`
3. Create a NEW ADR documenting the new decision
4. In the new ADR, reference the old ADR in the Context section
5. Explain what changed and why the new decision is better

**Example**:
```markdown
# ADR-001: Use PostgreSQL

## Status
Superseded by ADR-015

[rest of original ADR unchanged]
```

```markdown
# ADR-015: Migrate to CockroachDB

## Status
Accepted

## Context
ADR-001 selected PostgreSQL for production. Since then, we've experienced
challenges with geographic distribution and manual failover complexity.
CockroachDB offers PostgreSQL compatibility with built-in distributed
features...

[rest of new ADR]
```

## Spotting Hot Topics

If you see many ADRs superseding each other on the same topic, this indicates:
- An area of high uncertainty or rapid change
- Possibly insufficient information when making decisions
- Technology or requirements evolving faster than expected
- May need deeper analysis before next decision

This is valuable information that immutability preserves.

## Review Process

1. ADRs should be proposed via pull request
2. Discuss alternatives in the PR review
3. Status starts as "Proposed"
4. Once merged, update status to "Accepted"
5. Never modify after acceptance (except to mark as Superseded)

