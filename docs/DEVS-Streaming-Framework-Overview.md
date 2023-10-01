# DEVS Streaming Framework (DEVS-SF) Overview

## Systems and Their Models

A system is a black box.  It transforms a stream of inputs to a stream of outputs.

<img src="system.png" alt="system" width="70%" style="display: block; margin: 0 auto"/>

DEVS models a system as seen below.  If we know the current state, and the stream of inputs at a specific time, the state transition function determines the next state.  The output function determines the output from the current state.

<img src="system-model.png" alt="system model" width="70%" style="display: block; margin: 0 auto"/>

DEV-SF uses a parallel DEVS model as its implementation of a system model.  It interacts with external systems via messages, which leads to distributed, loosely coupled models.  Each input type (Sensor location, target location, sensor command) comes in through the input stream.  Control events (initialize, send output, execute transition, time) also come in through the input stream.  An event processor reads events from the input stream, processes them, and sends events to another set of streams.  It breaks state transition function into 3 cases:
* Internal state transition executes next internal event.
* External state transition based on receipt of inputs prior to next internal event.
* Confluent transition function executed when inputs are received as same time as next internal event.

It also adds time advance function to schedule next internal event for a model

<img src="parallel-devs-model.png" alt="Parallel DEVS Model" width="70%" style="display: block; margin: 0 auto"/>

Just as sub-systems can be coupled together via interfaces, system models are coupled by routing the outputs of models to the inputs of other models.

<img src="coupled-model.png" alt="Coupled model" width="70%" style="display: block; margin: 0 auto"/>

However, when viewed as a black box, a coupled system model behaves exactly as a system model.  More formally, DEVS models are *closed under coupling* because any network of coupled system models is itself a system model.  

<img src="coupled-model-black-box.png" alt="Coupled model viewed as black box" width="70%" style="display: block; margin: 0 auto"/>

This property enables the hierarchical construction of system models to potentially mirror the hierarchical structure of real systems, sub-systems, and components.

<img src="hierarchy-of-models.png" alt="Hierarchy of models" width="70%" style="display: block; margin: 0 auto"/>

DEVS-SF implements coupled models as a Parallel DEVS Coordinator.  With the same interface as a Parallel DEVS Model, it routes input/output streams to the influenced models through Parallel DEVS Couplings.  By managing time and execution of subordinate models, it yields discrete event model that allows stream of inputs, outputs and state to be computed for models in the structure.

<img src="parallel-devs-coordinator.png" alt="Parallel DEVS Coordinator" width="70%" style="display: block; margin: 0 auto"/>

## Example Sensor Network Models

As an example of the type of model hierarchy that suitable for DEVS modeling, consider a network of sensor models arrayed on a combat vehicle to detect threats on the battlefield.  This sensor network can be modeled abstractly with a simple probability of detection.  When a threat approaches, the output is a detection or non-detection based on a random draw.  

<img src="sensor-network.png" alt="Sensor Network" width="70%" style="display: block; margin: 0 auto"/>

However, the system is composed of several individual sensors, we will call sub-systems.  Each sensor can be modeled in a little more detail with a detection range and detection probability.  DEVS handles this by decomposing the network of sensor models into a set of coupled models as seen below.

<img src="coupled-sensor-models.png" alt="Coupled sensor models" width="70%" style="display: block; margin: 0 auto"/>

Finally, one of the sensors can be modeled by a set of sensor components, the lens, the detector array, and the display.  The models of each of these components are coupled together.  A hierarchical depictions of these models is shown below.

<img src="full-sensor-model-hierarchy.png" alt="Full sensor model hierarchy" width="70%" style="display: block; margin: 0 auto"/>

