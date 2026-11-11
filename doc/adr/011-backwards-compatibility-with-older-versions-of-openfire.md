# ADR-011: Backwards Compatibility With Older Versions of Openfire

## Status

Accepted

## Context

A release of Openfire does not run on its own. It exchanges data with things that were made by, or for, older versions of Openfire:

- **Other servers**: other Openfire servers, which upgrade on their own schedule, are on the other end of server-to-server connections. What one release sends, an older release has to accept, and the other way around.
- **Other cluster nodes**: where nodes of a cluster run different versions, for example while they are upgraded one by one, they exchange cluster tasks and cache entries.
- **Stored data and configuration**: the database, the properties, the configuration files and the keystores were written by an older version.
- **Behaviour that deployments rely on**: administrators, clients, components and other servers rely on defaults, timeouts, error conditions and the order of events that an older version showed, often without knowing it.
- **Plugins**: plugins call Openfire's public and protected members. ADR-010 covers those.

A change can break any of these without failing a unit test: the tests run one version of Openfire, against mocks or against itself. Two servers that run the same, new version can also agree on a behaviour that an older version rejects. Such a break is typically found after a release, by administrators whose federation, cluster or configuration stopped working.

At the same time, some changes are needed for correctness, and are incompatible by nature: refusing input that a specification forbids, or no longer sending something that a specification forbids. Openfire has dealt with this before. ADR-006 changed how encrypted values are stored, and kept decrypting values in the old format, so that data written by older versions stays readable.

## Options Considered

### Option 1: No Policy, Decide per Change

**Description**: Leave compatibility to the judgement of the contributor and the reviewer of each change.

**Consequences**:

- ✅ No process
- ❌ Whether compatibility is considered depends on who writes and reviews a change
- ❌ Incompatible changes can be merged without anyone noticing that they are incompatible

### Option 2: Never Break Compatibility

**Description**: No change may break compatibility with an older version of Openfire.

**Consequences**:

- ✅ Upgrades never break federation, clusters or configuration
- ❌ Openfire can never stop accepting input that a specification forbids, or stop sending output that a newer specification forbids, also where that is a security or correctness problem

### Option 3: Break Compatibility Where Correctness Needs It, and Document It

**Description**: Make the correct behaviour the only behaviour, and list the incompatibility in the changelog.

**Consequences**:

- ✅ Simple code, with one behaviour
- ❌ Deployments that depend on the old behaviour, for example servers that federate with older Openfire servers, break on upgrade, with no way back other than downgrading
- ❌ Interoperability between servers depends on both of them upgrading at the same time, which they don't

### Option 4: Compatible by Default; Incompatible Only When Flagged and Agreed, With a Switch Where Possible (CHOSEN)

**Description**: A change keeps compatibility with older versions of Openfire. Where an incompatible change is desirable, or hard to avoid for correctness, it is flagged and agreed to explicitly, and is preferably made switchable, so that a deployment can choose between the new and the old behaviour. Where possible, the change is staged: Openfire is first made compatible with the new behaviour, and the new behaviour follows behind a switch.

**Consequences**:

- ✅ Incompatibilities are decided on, rather than discovered after a release
- ✅ A deployment that is hurt by a new behaviour can switch back without downgrading
- ✅ Staging lets the servers and nodes that Openfire talks to upgrade to a compatible version before the new behaviour is turned on
- ❌ Two behaviours to implement, test and document, for as long as the switch exists
- ❌ The choice of each default needs an evaluation of its own

## Decision

We will implement **Option 4: Compatible by default; incompatible only when flagged and agreed, with a switch where possible**.

Specifically:

- **Compatibility is the default**: when code is added or refactored, it keeps working with the older versions of Openfire that a deployment may meet: as a peer on server-to-server connections, as another node of a cluster, as the writer of stored data and configuration, and in the behaviour that deployments rely on. For plugins, ADR-010 applies.
- **Incompatible changes are flagged and agreed**: a change that breaks compatibility with an older version says so explicitly in its commit message, in its pull request description, and in the changelog, naming what breaks and for whom. It is merged only once a maintainer has explicitly agreed to the incompatibility in the review of the pull request.
- **A switch where possible**: where an incompatible change is desirable, or hard to avoid for correctness, a configuration option (a system property) is considered that switches between the new behaviour and the compatible one. The description of the option says which behaviour it controls, and which versions or peers need the compatible one.
- **Staging**: where possible, the change is split. First, Openfire is made able to work with the new behaviour while it keeps the old one: for example, it accepts a form of input that it does not send yet, or it reads a format that it does not write yet. Then the new behaviour is added behind a switch. A later release can turn the switch on by default, once versions of Openfire that are compatible with it have been available for some time.
- **Defaults are decided per case**: which of the two behaviours is the default is decided for each switch on its own, after an evaluation of the pros and cons of each, including security, interoperability with older versions and with other implementations, and what an administrator has to do in either case. That evaluation is recorded where the change is planned or decided: an engineering plan, an ADR, or the pull request.
- **Removing a switch is a change of its own**: a switch that exists for compatibility is removed only as an incompatible change under this ADR, once the versions that need it are no longer in use to a degree that matters.

## Rationale

Most incompatibilities are not chosen but overlooked: the code works, the tests pass, and the break only shows against a version that was not part of the test. Requiring an explicit flag makes contributors and reviewers ask the question for every change, and makes the answer visible to administrators in the changelog.

A switch turns an incompatibility from a reason not to upgrade into a setting. It also gives a way back when the evaluation of a default turns out wrong for some deployments, without a new release.

Staging addresses the case where both ends of a connection, or all nodes of a cluster, have to change. If Openfire both starts to send something new and starts to require it in one release, two servers of different versions cannot talk to each other until both upgrade. If Openfire first learns to accept it, and only later sends or requires it, every pair of consecutive releases stays compatible.

Not every change can be made switchable at a reasonable cost, and some old behaviours are a security problem that should not be available at all. That is why a switch is considered rather than required, and why the incompatibility then has to be agreed on explicitly.

## Consequences

### What Becomes Easier

- Administrators can upgrade without coordinating with the administrators of servers they federate with, and can upgrade cluster nodes one by one
- An incompatibility can be undone in the field, by a setting
- Reviewers have a rule to point to when a change breaks compatibility without saying so

### What Becomes Harder

- Contributors have to know what older versions send, accept and store, which may mean reading older code
- Switches add code paths and documentation, and some of them have to be removed again later
- Deciding a default takes an evaluation, rather than picking the new behaviour

### Constraints

- Every change that breaks compatibility with an older version of Openfire is flagged in its commit message, pull request and the changelog, and is agreed to by a maintainer before it is merged
- Tests between two servers that both run the change cannot show compatibility with older versions; where compatibility matters, a test against an older version of Openfire is part of the verification of the change

## References

- **ADR-006**: Migrate AES Encryption from CBC to GCM Mode (a change in stored data, made compatible by reading the old format)
- **ADR-010**: Compatibility of Public Methods for Plugins
- **OF-2425**: Stream Management for server-to-server connections (engineering plan: `doc/plan/2026-10-23-stream-management-s2s.md`), which puts its new behaviour behind switches that are off by default
