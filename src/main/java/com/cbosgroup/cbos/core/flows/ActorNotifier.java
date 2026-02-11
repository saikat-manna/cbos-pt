package com.cbosgroup.cbos.core.flows;

import com.cbosgroup.cbos.core.actors.Actor;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;

import java.util.List;

/**
 * Interface for notifying actors when a user task requires their response.
 * Implementations can send email, SMS, push notifications, etc.
 */
public interface ActorNotifier {

    /**
     * Notify actors that a user task is waiting for their response.
     *
     * @param actors list of actors to notify
     * @param task the user task awaiting response
     * @param flowInstance the flow instance context
     */
    void notifyActors(List<Actor> actors, UserTaskNode task, FlowInstance flowInstance);
}
