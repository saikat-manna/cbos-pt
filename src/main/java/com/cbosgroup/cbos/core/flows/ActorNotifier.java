package com.cbosgroup.cbos.core.flows;

import com.cbosgroup.cbos.core.actors.Actor;

import java.util.List;

/**
 * Interface for notifying actors when a user task requires their response.
 * Implementations can send email, SMS, push notifications, etc.
 */
public interface ActorNotifier {

    void notifyActors(List<Actor> actors, UserTaskNode task);
}
