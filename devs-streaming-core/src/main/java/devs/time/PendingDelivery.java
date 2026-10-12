package devs.time;

import devs.iso.PortValue;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.SimTime;

/**
 * Source time remains distinct from the timestamp accepted by the destination.
 * The record itself performs no validation; {@link CanonicalSchedule} creates validated entries.
 * @param interaction directed model and port identifiers
 * @param portValue payload awaiting delivery
 * @param sourceTime original emission time in canonical seconds
 * @param effectiveTime accepted destination timestamp expressed in canonical seconds
 * @param nativeTime accepted timestamp in the destination's native units and encoding
 * @param sequence insertion sequence used to break equal-effective-time ties
 */
public record PendingDelivery(InteractionKey interaction, PortValue<?> portValue,
    DecimalSimTime sourceTime, DecimalSimTime effectiveTime, SimTime nativeTime,
    long sequence) {}
