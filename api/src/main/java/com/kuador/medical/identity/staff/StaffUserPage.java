package com.kuador.medical.identity.staff;

import java.util.List;

/** {@code StaffUserPage} of the contract. */
record StaffUserPage(int page, int size, long totalItems, List<StaffUserResponse> items) {}
