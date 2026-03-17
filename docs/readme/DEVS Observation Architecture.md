# DEVS Observation Architecture

## Overview

This architecture defines a high-performance, data-efficient method for capturing and storing simulation results from a DEVS (Discrete Event System Specification) simulation. It leverages the determinism of DEVS to minimize persistent storage while providing a robust mechanism for "what-if" branching and historical replay.

## Core Terminology

| Term | Definition |
| :--- | :--- |
| **Observation** | A single, time-stamped capture of data from a model. It represents the "fact" that occurred at logical time *T* in a specific branch. |
| **ObservationType** | Defines the structure, fields, and versioning of a specific kind of observation (e.g., `UnitPosition`, `BatteryStatus`). It may include a type identifier to facilitate deserialization in specific languages or frameworks. |
| **ObservationFactory** | The component or logic responsible for transforming internal `ModelState` into a structured `Observation` according to an `ObservationType`. |
| **ObservationArchive** | The persistent storage where all observations of a certain type are stored for a given run (e.g., a database table, collection, or time-series bucket). |
| **Observation Context** | The combination of `runId` and `branchId` that uniquely identifies the timeline of an observation. |

## Architectural Principles

1.  **Determinism-Based Storage:** Instead of saving the entire internal state of every model at every step, we only store:
    *   **Minimal Specification:** Initial conditions, random seeds, and external branch decisions.
    *   **Observations:** The "observable" outputs needed for visualization, analysis, or playback.
2.  **Pure DEVS Observation:** Observation is a first-class citizen. Models emit observations through dedicated `Ports` during their output function ($\lambda$).
3.  **Asynchronous Persistence:** To prevent I/O bottlenecks, observations are passed to an `Observer` model that bridges the simulation to an asynchronous execution environment (e.g., an Actor System, Message Queue, Thread Pool, or non-blocking I/O service) for persistence writes. This ensures that slow I/O does not block simulation logical time.
4.  **Heterogeneous Observation:** Multiple observers can be attached to the same `Port`. This allows simultaneous storage to multiple backends (e.g., MongoDB for history, SQL for relational analysis, and a real-time web socket for live monitoring).
5.  **Parallelization of Writes:** By using asynchronous bridges, persistence operations can be parallelized across multiple workers or services, optimizing the overall throughput of the simulation environment.
6.  **Branching Timeline Model:** Simulations can "fork" into multiple alternate timelines. Each branch is identified by a `branchId` and its parent's lineage.
7.  **Flat Run Support:** For simulations without branching, a default "root" or null `branchId` is used, ensuring the architecture is usable for 90% of standard simulation cases without extra overhead.

## JSON Schema Definitions

To ensure data integrity and facilitate automated validation, the following JSON schemas define the structure of the core collections.

### 1. Run Schema (`runs.schema.json`)
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

### 2. Branch Schema (`branches.schema.json`)
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

### 3. Observation Schema (`observation.schema.json`)
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

## Data Store Schemas (Persistence Layer)

The architecture is agnostic to the underlying persistence technology. Below are examples of how the core entities are represented, using a document-oriented structure (like MongoDB) for illustration.

### 1. `runs` Data Point
Stores metadata about a simulation execution.

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

### 2. `branches` Data Point
Defines the lineage of different timelines within a run.

```json
{
  "_id": "branch-uuid",
  "runId": "run-uuid",
  "parentBranchId": "parent-branch-uuid-or-null",
  "forkTime": 3600,
  "description": "Alternate reconnaissance path with low battery threshold"
}
```
*Note: Every run has at least one "root" branch with `parentBranchId: null` and `forkTime: 0`.*

### 3. `observation_archives` (Sample Record)
Observations of a specific type (e.g., `Observation_UnitPosition`) stored in their respective archive.

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
*Note: The `observationType` field stores a unique identifier (such as a class name or schema ID) to facilitate serialization and deserialization of the `payload`, which is an instance of that specific `ObservationType`.*

## Data Flow Summary

1.  **Model Logic:** The DEVS model updates its internal state during a transition.
2.  **Production:** When an observation is required, the **ObservationFactory** creates an **Observation** from the state.
3.  **Emission:** The model emits the **Observation** via its dedicated **Port**.
4.  **Routing:** The **Simulation Coordinator** routes the message to one or more **Observers** (e.g., MongoDB, SQL, or Real-Time).
5.  **Asynchronous Bridge:** Each **Observer** performs a "fire-and-forget" call to its respective persistence service or handler (e.g., `PersistenceHandler.send(observation)`).
6.  **Persistence:** The target service writes the **Observation** to the corresponding **ObservationArchive** asynchronously.
