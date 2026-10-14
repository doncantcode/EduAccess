package com.eduaccess.service;

import com.eduaccess.model.Role;
import com.eduaccess.model.User;

/** Permission checks for the actions only library admins may perform. */
final class Access {

    private Access() {
    }

    static void requireAdmin(User user, String action) {
        if (user == null || user.role() != Role.ADMIN) {
            throw new ServiceException("Only library admins can " + action + ".");
        }
    }
}
