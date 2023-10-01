# DEVS Streaming Framework
*Protocol for composing Distributed DEVS Models Across different
Simulation Frameworks*

The Discrete Event System Specification Streaming Framework (DEVS-SF) is
a data exchange specification that allows composition of DEVS models
running on different computers, potentially built with different DEVS
frameworks, into a single simulation run. It is a JSON data
specification developed for exchange over commercially available
streaming platforms, such as Apache Kafka, so simulation architectures
can be built using open-source technologies. The use of the DEVS-SF by
an existing DEVS implementation requires the development of a DEVS
simulator to consume DEVS-SF messages from an event stream, execute its
associated atomic or coupled model, and publish events back to an event
stream. DEVS-SF also enables the development of DEVS wrappers around
non-modular simulation architectures, such as High Level Architecture
(HLA) of the US Army's Bifrost[^1] server, so they may execute as models
in a DEVS simulation. Simlytics.cloud's Java implementation of DEVS-SF
is available as open-source software.

[^1]: US Army Program Executive Office -- Simulation, Training, and
    Instrumentation (PEO-STRI), One Semi-Automated Forces (OneSAF),
    available at <https://www.peostri.army.mil/onesaf>.
