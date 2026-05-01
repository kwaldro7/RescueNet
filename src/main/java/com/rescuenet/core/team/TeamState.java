package com.rescuenet.core.team;

/** Operational state of a response team. */
public enum TeamState {
    AVAILABLE,
    EN_ROUTE,
    ON_SCENE,
    UNAVAILABLE;

    /**
     * Returns true if transitioning to the target state is valid.
     * Enforces the state machine and prevents illegal transitions.
     */
    public boolean canTransitionTo(TeamState target) {
        switch (this) {
            case AVAILABLE:   return target == EN_ROUTE   || target == UNAVAILABLE;
            case EN_ROUTE:    return target == ON_SCENE   || target == AVAILABLE;
            case ON_SCENE:    return target == AVAILABLE  || target == UNAVAILABLE;
            case UNAVAILABLE: return target == AVAILABLE;
            default:          return false;
        }
    }
}
