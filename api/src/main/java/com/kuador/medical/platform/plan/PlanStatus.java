package com.kuador.medical.platform.plan;

/** A plan is never deleted: it is archived and existing subscriptions keep it. */
public enum PlanStatus {
    ACTIVE,
    ARCHIVED
}
