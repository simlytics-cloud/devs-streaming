# DEVS Observation Architecture

## Overview

This document describes the DEVS observation architecture in three layers:

1. an implementation-neutral architecture that can be applied in different DEVS frameworks and programming languages
2. the current DEVS Streaming Java mapping of that architecture
3. the two current persistence implementations in this codebase: MongoDB and PostgreSQL

This separation is intentional. The architecture itself is not tied to Java, actors, MongoDB, PostgreSQL, or any one DEVS library. Those technologies are the current realization used in the DEVS Streaming Framework.

## Level 1: Implementation-Neutral Observation Architecture

### Purpose

The observation architecture defines a high-performance, data-efficient method for capturing and storing simulation results from a DEVS (Discrete Event System Specification) simulation. It leverages the determinism of DEVS to minimize persistent storage while still supporting historical replay, analysis, and what-if branching.

### Core Terminology

| Term | Definition |
| :--- | :--- |
| **Run** | A single simulation execution context. |
| **Branch** | A timeline within a run. A branch may fork from another branch to represent an alternate scenario. |
| **Observation** | A time-stamped capture of model output or other observable simulation information. |
| **ObservationType** | The logical type of an observation payload. It defines the meaning and structure of a category of observations. |
| **ObservationFactory** | The logic that transforms model state or model output into an observation payload. |
| **ObservationArchive** | The physical storage location for persisted observations of a given type. This may be a table, collection, bucket, stream, or other backend-specific structure. |
| **Observation Context** | The identifying context for an observation, typically including at least `runId` and `branchId`. |
| **Observation Catalog** | A registry of observation types and their associated physical archive locations and metadata. |
| **Observation Sink** | A persistence component that accepts observation traffic and writes it to a target backend. |

### Architectural Principles

1. **Determinism-Based Storage:** Instead of storing full internal model state at every step, the architecture stores the minimum information needed to reproduce or analyze a simulation:
   - initial conditions, seeds, and external decisions
   - observations required for playback, visualization, or analysis
2. **Pure DEVS Observation:** Observation is treated as a first-class concern. Models emit observations as part of their normal DEVS behavior, typically during output generation.
3. **Asynchronous Persistence:** Observation capture should not force slow persistence operations into the simulation's logical time path. Observation handling should therefore be decoupled from backend writes.
4. **Heterogeneous Backends:** The same logical observation stream may be consumed by more than one persistence backend at the same time.
5. **Parallel Write Paths:** Persistence operations may be parallelized or delegated to separate workers, services, or processes for throughput.
6. **Branching Timeline Model:** A run may contain multiple branches to support what-if analysis and replay of alternate decisions.
7. **Flat Run Support:** Systems that do not need branching can still use the same architecture with a single default or root branch.

### Logical Data Model

Any implementation of this architecture should support persistence of at least these logical entities:

- `Run`
- `Branch`
- `ObservationTypeEntry` or an equivalent observation catalog record
- `Observation`

The architecture does not require one physical storage pattern. A document database, relational database, time-series store, event log, or hybrid design can all implement the same logical model.

### JSON Schema Definitions

The following schemas illustrate the logical structure of the core entities. They are intended as portable architecture examples rather than Java-specific API definitions.

#### 1. Run Schema (`runs.schema.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Run",
  "type": "object",
  "properties": {
    "_id": { "type": "string", "description": "Unique identifier for the run (UUID)" },
    "name": { "type": "string", "description": "Human-readable name of the simulation run" },
    "startTime": { "type": "string", "format": "date-time", "description": "Wall-clock start time" },
    "status": { "enum": ["PENDING", "RUNNING", "COMPLETED", "FAILED"] },
    "config": {
      "type": "object",
      "additionalProperties": true
    }
  },
  "required": ["_id", "name", "startTime", "status"]
}
```

#### 2. Branch Schema (`branches.schema.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Branch",
  "type": "object",
  "properties": {
    "_id": { "type": "string", "description": "Unique identifier for the branch (UUID)" },
    "runId": { "type": "string", "description": "Reference to the parent run" },
    "parentBranchId": { "type": ["string", "null"], "description": "Reference to the parent branch, null for root" },
    "forkTime": { "type": "number", "description": "Logical simulation time when the branch forked" },
    "description": { "type": "string" }
  },
  "required": ["_id", "runId", "forkTime"]
}
```

#### 3. Observation Schema (`observation.schema.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Observation",
  "type": "object",
  "properties": {
    "_id": { "type": "string" },
    "runId": { "type": "string" },
    "branchId": { "type": "string" },
    "simulationTime": { "type": "number" },
    "producerModel": { "type": "string" },
    "observationType": { "type": "string", "description": "Identifier for the observation payload (e.g., a fully qualified class name or a schema registry ID)" },
    "payload": {
      "type": "object",
      "description": "The specific data structure defined by the ObservationType"
    }
  },
  "required": ["_id", "runId", "branchId", "simulationTime", "observationType", "payload"]
}
```

#### 4. Observation Type Catalog Schema (`observation_type_catalog.schema.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ObservationTypeEntry",
  "type": "object",
  "properties": {
    "typeId": { "type": "string", "description": "Unique identifier (e.g., 'Observation_UnitPosition')" },
    "archiveName": { "type": "string", "description": "The name of the database table/collection (e.g., 'obs_unit_positions')" },
    "metadata": {
      "type": "object",
      "description": "Optional generic metadata for UI hints (e.g., displayName, category, icon)",
      "additionalProperties": true
    }
  },
  "required": ["typeId", "archiveName"]
}
```

### Data Store Schemas (Persistence Layer)

The architecture is intentionally agnostic to the underlying persistence technology. The following examples show the logical entities using a document-like representation for clarity.

#### 1. `runs` Data Point

```json
{
  "_id": "run-uuid",
  "name": "CWIX 2025 Autonomous Recon",
  "startTime": "2026-03-17T06:45:00Z",
  "status": "COMPLETED",
  "config": {
    "seed": 12345,
    "scenarioFile": "recon_mission.xml"
  }
}
```

#### 2. `branches` Data Point

```json
{
  "_id": "branch-uuid",
  "runId": "run-uuid",
  "parentBranchId": "parent-branch-uuid-or-null",
  "forkTime": 3600,
  "description": "Alternate reconnaissance path with low battery threshold"
}
```

*Note: Every run has at least one root branch with `parentBranchId: null` and `forkTime: 0`.*

#### 3. `observation_archives` Sample Record

```json
{
  "_id": "obs-uuid",
  "runId": "run-uuid",
  "branchId": "branch-uuid",
  "simulationTime": 3660,
  "producerModel": "uav-01",
  "observationType": "Observation_UnitPosition",
  "payload": {
    "lat": 51.5074,
    "lon": -0.1278,
    "alt": 150.0,
    "heading": 90.0
  }
}
```

#### 4. `observation_type_catalog` Sample Record

```json
{
  "typeId": "Observation_UnitPosition",
  "archiveName": "obs_unit_positions",
  "metadata": {
    "displayName": "Unit Position",
    "category": "Entity",
    "icon": "mdi-map-marker",
    "schemaId": "unit_position.schema.json"
  }
}
```

*Note: The `observationType` field stores a unique identifier such as a class name, schema ID, or registry key so the payload can be interpreted correctly by the target framework.*

### Data Flow Summary

1. **Model Logic:** The DEVS model updates its internal state during a transition.
2. **Production:** When an observation is required, an `ObservationFactory` or equivalent component creates an observation from the model state or output.
3. **Emission:** The model emits the observation through its normal DEVS interaction mechanism.
4. **Routing:** An observation-routing layer forwards observation traffic to one or more persistence sinks.
5. **Persistence:** Each sink writes runs, branches, catalog entries, and observations to backend-specific archives.
6. **Shutdown:** Observation capture stops only after pending writes are completed or safely handed off.

### Custom Backend Pattern

This architecture is intentionally backend-neutral. Developers can implement additional persistence backends by following the same general pattern:

- define or reuse a shared observation message model
- separate the simulation-facing observation contract from backend-specific persistence code
- implement one or more persistence sinks for the target backend
- persist runs, branches, observation catalog entries, and observations
- maintain a mapping from logical observation types to physical archives

## Level 2: DEVS Streaming Java Mapping

### Current Java Structure

In the DEVS Streaming Framework, the backend-neutral observation model and actor contracts live in `devs-streaming-core` under `devs.observation`.

The current Java mapping is built around shared contracts in `devs-streaming-core` and pluggable sink implementations in separate modules.

- `ObservationModel` is the simulation-facing observation router.
- `DevsObservationMessage` is the shared message contract for observation traffic.
- `Observation`, `ObservationTypeEntry`, `Run`, and `Branch` are the persisted message types handled by sinks.
- `StopLogger` coordinates graceful sink shutdown after pending writes complete.
- Sink actors register with `ObservationSinkKeys.OBSERVATION_SINK_KEY`, allowing backend implementations to be swapped or combined without changing the core simulation-facing contract.

### Java-Specific Interpretation of Core Terms

| Architecture Concept | DEVS Streaming Java Mapping |
| :--- | :--- |
| Observation | A concrete `DevsObservationMessage` instance, typically `Observation<?, ?>` |
| Observation Type Catalog | Persisted as `ObservationTypeEntry` messages |
| Observation Sink | A backend-specific actor that consumes `DevsObservationMessage` |
| Routing Layer | `ObservationModel` plus service-key based sink discovery |
| Graceful Shutdown | `StopLogger` handled by sink actors after pending writes complete |

### Java Data Flow Summary

1. **Model Logic:** The DEVS model updates its internal state during a transition.
2. **Production:** When an observation is required, application logic creates an `Observation` from the state.
3. **Emission:** The model emits the `Observation` via its dedicated port or observation path.
4. **Routing:** The observation layer routes `DevsObservationMessage` traffic through `ObservationModel` to one or more registered sink actors using `ObservationSinkKeys.OBSERVATION_SINK_KEY`.
5. **Sink Handling:** Each sink actor accepts `Run`, `Branch`, `ObservationTypeEntry`, `Observation`, and `StopLogger` messages and forwards persistence work to its backend-specific write path.
6. **Persistence:** The target backend writes runs, branches, observation type catalog entries, and observations to the appropriate archives.
7. **Shutdown:** When observation capture should stop, `StopLogger` is used so sinks can finish pending writes before shutting down.

### Java Extension Pattern

Within DEVS Streaming Java, additional persistence backends should follow the established module pattern:

- keep the shared observation model and registration contract in `devs-streaming-core`
- isolate backend dependencies in a dedicated module
- implement a sink actor that consumes `DevsObservationMessage`
- register that sink with `ObservationSinkKeys.OBSERVATION_SINK_KEY`
- persist runs, branches, `ObservationTypeEntry` records, and per-type observations using the conventions of the target backend

## Level 3: Current Persistence Implementations in DEVS Streaming Java

### Overview

The current codebase provides two concrete persistence implementations:

- `devs-observation-mongodb`
- `devs-observation-postgresql`

Both modules:

- build on the shared contracts in `devs-streaming-core`
- register their sink actors with `ObservationSinkKeys.OBSERVATION_SINK_KEY`
- consume the shared `DevsObservationMessage` model
- persist the same logical entities: runs, branches, observation type catalog entries, and observations

### MongoDB Implementation

The MongoDB sink is the document-store reference implementation for this architecture.

- It persists the logical observation model using MongoDB collections.
- It uses per-type archives in a document-oriented form.
- It demonstrates the backend-isolated sink-module pattern with a document database.

### PostgreSQL Implementation

The PostgreSQL sink persists the same logical model using relational structures.

- `runs`, `branches`, and `observation_type_catalog` are shared base tables.
- Each logical observation type maps to its own archive table.
- Archive table names are sanitized from the logical observation type before creation.
- Structured fields such as time and payload are stored alongside the full serialized observation document.

### Why Both Matter

These two implementations show that the same observation architecture can be realized using different persistence models:

- MongoDB demonstrates a document-oriented backend.
- PostgreSQL demonstrates a relational backend with per-type archive tables.

Together they provide concrete examples for developers who want to implement additional backends in Java or adapt the same architecture to other languages and DEVS frameworks.