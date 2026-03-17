Retailer Model Tutorial — Scheduled DEVS Implementation Guide

This tutorial page explains how the Retailer model is implemented for the Inventory Routing Problem using the DEVS Streaming Framework model pattern. It provides the architectural background needed to complete the RetailerImpl exercise class.

The Retailer model is built using the DEVS Streaming Framework’s ScheduledDevsModel. Much of the simulation timing and event mechanics are already implemented in the framework. Your job is to implement the domain behavior, not the simulation engine mechanics.

The inheritance chain is:

ScheduledDevsModel (DEVS Streaming Framework)
↑
Facility (generated)
↑
Retailer (generated)
↑
RetailerImpl (your implementation)
ScheduledDevsModel Execution Model

A typical DEVS atomic model reasons about time using a time advance functions and state transitions. While this logic is mathematically necessary and succinct for DEVS formulation, it is different than how simulation developers typically think about models with a clock and event schedule. A ScheduledDevsModel provides a DEVS compliant atomic model with an internal schedule. Its internal state has a schedule, a time-ordered TreeMap with a list of events and outputs at each time.

Because of this, the framework already implements several DEVS functions for you.

Time Advance Function

The time advance function is already implemented.

Behavior:

Returns the interval between:

current simulation time
the first scheduled item in the schedule
You do not implement time advance yourself.

Output Function

The output function is already implemented.

Behavior:

Returns a bag of PortValues
Includes all outputs scheduled for the current simulation time
Outputs are pulled directly from the schedule.

Internal State Transition

The internal transition function is also implemented by the framework.

It automatically:

Advances current simulation time
Removes published outputs from the schedule
Retrieves all scheduled internal events at the current time
Passes those events to your handler method
You do NOT override the internal transition directly.

Instead, you implement:

public void handleScheduledEvents(List<Object> events)
This is where your model reacts to scheduled internal events.

Working with the Schedule

The schedule supports both internal events and scheduled outputs. A common pattern is to define inner classes to represent internal events.

Example — schedule a store opening event 6 hours after simulation start:

modelState.getSchedule().scheduleInternalEvent(
LongSimTime.create(60 * 6),
new OpenEvent()
);
Typical usage:

define small inner event classes
schedule them at future times
handle them inside handleScheduledEvents
You can schedule output port values directly onto the schedule.

Example — publish daily inventory cost on the Retailers dailyInventoryCost port:

modelState.getSchedule().scheduleOutput(
currentTime, // Time of the scheduled output
Retailer.dailyInventoryCost, // Port on which to place the output
immutableInventoryCost // The output data structure consitent with the port type
);
These outputs will automatically be emitted by the framework output function at the scheduled time.

Role of the Generated Classes

Much of the DEVS struture for this project was automatically generated into the generated package.